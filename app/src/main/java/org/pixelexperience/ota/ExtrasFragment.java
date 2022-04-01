package org.pixelexperience.ota;


import static android.provider.OpenableColumns.DISPLAY_NAME;

import android.app.Activity;
import android.app.ProgressDialog;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.database.Cursor;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import android.os.ParcelFileDescriptor;
import androidx.fragment.app.Fragment;

import androidx.appcompat.app.AlertDialog;
import com.google.android.material.snackbar.Snackbar;

import org.pixelexperience.ota.misc.Utils;
import org.pixelexperience.ota.model.MaintainerInfo;
import org.pixelexperience.ota.model.UpdateInfo;
import org.pixelexperience.ota.model.Update;
import org.pixelexperience.ota.model.UpdateStatus;
import org.pixelexperience.ota.controller.UpdaterController;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.ArrayList;

public class ExtrasFragment extends Fragment {

    private View mainView;
    private LinearLayout maintainersLayout;
    private ExtraCardView donateCard;
    private ExtraCardView forumCard;
    private ExtraCardView websiteCard;
    private ExtraCardView newsCard;
    private ExtraCardView localUpdateCard;

    private static final int SELECT_FILE = 1001;
    private static final String TAG = "ExtrasFragment";
    private static final String MIME_ZIP = "application/zip";
    private static final String METADATA_PATH = "META-INF/com/android/metadata";
    private static final String METADATA_TIMESTAMP_KEY = "post-timestamp=";

    private Thread workingThread;

    private ProgressDialog importDialog;

    private UpdateListener mListener;
    public interface UpdateListener {
        public void addedUpdate(Update update);
        public void importDisabled();
        public void importFailed();
    }

    @Override
    public void onAttach(Activity activity) {
        mListener = (UpdateListener) activity;
        super.onAttach(activity);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mainView = inflater.inflate(R.layout.extras_fragment, container, false);
        localUpdateCard = mainView.findViewById(R.id.local_update_card);
        maintainersLayout = mainView.findViewById(R.id.maintainers);
        donateCard = mainView.findViewById(R.id.donate_card);
        forumCard = mainView.findViewById(R.id.forum_card);
        websiteCard = mainView.findViewById(R.id.website_card);
        newsCard = mainView.findViewById(R.id.news_card);
        return mainView;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (requestCode == SELECT_FILE && resultCode == Activity.RESULT_OK && resultData != null) {
            Uri uri = resultData.getData();
            Cursor cursor = getActivity().getContentResolver().query(uri,
                    null, null, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                String fileName = cursor.getString(cursor.getColumnIndex(DISPLAY_NAME));
                if (importDialog != null && importDialog.isShowing()) {
                    importDialog.dismiss();
                }
                importDialog = ProgressDialog.show(getContext(), getString(R.string.local_update_title),
                        getString(R.string.local_update_import_progress), true, false);
                workingThread = new Thread(() -> {
                    File importedFile = null;
                    try {
                        importedFile = importFile(uri, fileName);
                        verifyPackage(importedFile);

                        final Runnable deleteUpdate = () -> Utils.cleanupDownloadsDir(getContext());

                        final Update update = buildLocalUpdate(importedFile, fileName);
                        getActivity().runOnUiThread(() -> {
                            if (importDialog != null) {
                                importDialog.dismiss();
                                importDialog = null;
                            }
                            new AlertDialog.Builder(getContext())
                                .setTitle(R.string.local_update_title)
                                .setMessage(getString(R.string.local_update_import_success, update.getName()))
                                .setPositiveButton(R.string.local_update_import_install, (dialog, which) -> {
                                    mListener.addedUpdate(update);
                                })
                                .setNegativeButton(android.R.string.cancel, (dialog, which) -> deleteUpdate.run())
                                .setOnCancelListener((dialog) -> deleteUpdate.run())
                                .show();
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to import update package", e);
                        // Do not store invalid update
                        if (importedFile != null) {
                            importedFile.delete();
                        }

                        getActivity().runOnUiThread(() -> {
                            if (importDialog != null) {
                                importDialog.dismiss();
                                importDialog = null;
                            }
                            mListener.importFailed();
                        });
                    }
                });
                workingThread.start();
            }
        }
    }

    @Override
    public void onPause() {
        if (importDialog != null) {
            importDialog.dismiss();
            importDialog = null;
            if (workingThread != null && workingThread.isAlive()) {
                workingThread.interrupt();
                workingThread = null;
            }
        }

        super.onPause();
    }

    private Update buildLocalUpdate(File file, String fileName) {
        final long timeStamp = getTimeStamp(file);
        final Update update = new Update();
        update.setName(fileName);
        update.setFile(file);
        update.setFileSize(file.length());
        update.setDownloadId(Update.LOCAL_ID);
        update.setTimestamp(timeStamp * 1000);
        update.setStatus(UpdateStatus.VERIFIED);
        return update;
    }

    private long getTimeStamp(File file) {
        try {
            final String metadataContent = readZippedFile(file, METADATA_PATH);
            final String[] lines = metadataContent.split("\n");
            for (String line : lines) {
                if (!line.startsWith(METADATA_TIMESTAMP_KEY)) {
                    continue;
                }

                final String timeStampStr = line.replace(METADATA_TIMESTAMP_KEY, "");
                return Long.parseLong(timeStampStr);
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to read date from local update zip package", e);
        } catch (NumberFormatException e) {
            Log.e(TAG, "Failed to parse timestamp number from zip metadata file", e);
        }

        Log.e(TAG, "Couldn't find timestamp in zip file, falling back to $now");
        return System.currentTimeMillis();
    }

    private String readZippedFile(File file, String path) throws IOException {
        final StringBuilder sb = new StringBuilder();
        InputStream iStream = null;

        try {
            final ZipFile zip = new ZipFile(file);
            final Enumeration<? extends ZipEntry> iterator = zip.entries();
            while (iterator.hasMoreElements()) {
                final ZipEntry entry = iterator.nextElement();
                if (!METADATA_PATH.equals(entry.getName())) {
                    continue;
                }

                iStream = zip.getInputStream(entry);
                break;
            }

            if (iStream == null) {
                throw new FileNotFoundException("Couldn't find " + path + " in " + file.getName());
            }

            final byte[] buffer = new byte[1024];
            int read;
            while ((read = iStream.read(buffer)) > 0) {
                sb.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to read file from zip package", e);
            throw e;
        } finally {
            if (iStream != null) {
                iStream.close();
            }
        }

        return sb.toString();
    }

    @SuppressLint("SetWorldReadable")
    @SuppressWarnings("ResultOfMethodCallIgnored")
    private File importFile(Uri uri, String fileName) throws IOException {
        final ParcelFileDescriptor parcelDescriptor = getActivity().getContentResolver()
                .openFileDescriptor(uri, "r");
        if (parcelDescriptor == null) {
            throw new IOException("Failed to obtain fileDescriptor");
        }

        final FileInputStream iStream = new FileInputStream(parcelDescriptor
                .getFileDescriptor());
        final File outFile = new File(Utils.getDownloadPath(), fileName);
        if (outFile.exists()) {
            outFile.delete();
        }
        final FileOutputStream oStream = new FileOutputStream(outFile);

        int read;
        final byte[] buffer = new byte[4096];
        while ((read = iStream.read(buffer)) > 0) {
            oStream.write(buffer, 0, read);
        }
        oStream.flush();
        oStream.close();
        iStream.close();

        outFile.setReadable(true, false);

        return outFile;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    private void verifyPackage(File file) throws Exception {
        try {
            android.os.RecoverySystem.verifyPackage(file, null, null);
        } catch (Exception e) {
            if (file.exists()) {
                file.delete();
                throw new Exception("Verification failed, file has been deleted");
            } else {
                throw e;
            }
        }
    }

    void updatePrefs(UpdateInfo update) {
        localUpdateCard.setOnClickListener(v -> {
            final UpdaterController updateController = UpdaterController.getInstance(getContext());
            if (updateController.isInstallingUpdate() ||
                    updateController.isDownloading()) {
                mListener.importDisabled();
            } else {
                final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType(MIME_ZIP);
                startActivityForResult(intent, SELECT_FILE);
            }
        });
        localUpdateCard.setClickable(true);
        localUpdateCard.setVisibility(View.VISIBLE);
        Log.d("ExtrasFragment:updatePrefs", "called");
        if (update == null) {
            Log.d("ExtrasFragment:updatePrefs", "update is null");
            mainView.setVisibility(View.GONE);
            return;
        }

        ArrayList<MaintainerInfo> maintainers = update.getMaintainers();
        if (maintainers != null && !maintainers.isEmpty()) {
            maintainersLayout.removeAllViews();
            for (MaintainerInfo maintainer : maintainers) {
                ExtraCardView maintainerCard = createMaintainerCard(getActivity());
                maintainerCard.setSummary(maintainer.getName());
                maintainerCard.setOnClickListener(v -> openUrl(Utils.getMaintainerURL(maintainer.getUsername())));
                maintainerCard.setClickable(true);
                maintainersLayout.addView(maintainerCard);
            }
        }

        if (update.getDonateUrl() != null && !update.getDonateUrl().isEmpty()) {
            donateCard.setOnClickListener(v -> openUrl(update.getDonateUrl()));
            donateCard.setClickable(true);
            donateCard.setVisibility(View.VISIBLE);
        }

        if (update.getForumUrl() != null && !update.getForumUrl().isEmpty()) {
            forumCard.setOnClickListener(v -> openUrl(update.getForumUrl()));
            forumCard.setClickable(true);
            forumCard.setVisibility(View.VISIBLE);
        }

        if (update.getWebsiteUrl() != null && !update.getWebsiteUrl().isEmpty()) {
            websiteCard.setOnClickListener(v -> openUrl(update.getWebsiteUrl()));
            websiteCard.setClickable(true);
            websiteCard.setVisibility(View.VISIBLE);
        }

        if (update.getNewsUrl() != null && !update.getNewsUrl().isEmpty()) {
            newsCard.setOnClickListener(v -> openUrl(update.getNewsUrl()));
            newsCard.setClickable(true);
            newsCard.setVisibility(View.VISIBLE);
        }

    }

    private ExtraCardView createMaintainerCard(Context context) {
        ExtraCardView card = new ExtraCardView(context);
        card.setTitle(getString(R.string.maintainer_info_title));
        card.setImage(getResources().getDrawable(R.drawable.ic_maintainers_icon, context.getTheme()));
        card.setCardBackgroundColor(getResources().getColor(R.color.cardview_background, context.getTheme()));
        card.setRadius(getResources().getDimension(R.dimen.extra_card_corner_radius));
        card.setCardElevation(getResources().getDimension(R.dimen.extra_card_elevation));
        int padding = (int) getResources().getDimension(R.dimen.extra_card_content_padding);
        card.setContentPadding(padding, padding, padding, padding);
        int extraMargin = (int) getResources().getDimension(R.dimen.extra_card_layout_margin);
        LinearLayout.LayoutParams buttonLayoutParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        buttonLayoutParams.setMargins(extraMargin, extraMargin, extraMargin, extraMargin);
        card.setLayoutParams(buttonLayoutParams);
        return card;
    }

    private void showSnackbar(int stringId, int duration) {
        Snackbar.make(getActivity().findViewById(R.id.main_container), stringId, duration).show();
    }

    private void openUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        } catch (Exception ex) {
            showSnackbar(R.string.error_open_url, Snackbar.LENGTH_SHORT);
        }
    }
}
