package org.pixelexperience.ota;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;

import com.google.android.material.snackbar.Snackbar;

import org.pixelexperience.ota.model.UpdateInfo;

public class ExtrasFragment extends Fragment {

    private View mainView;
    private ExtraCardView maintainerCard;
    private ExtraCardView donateCard;
    private ExtraCardView forumCard;
    private ExtraCardView websiteCard;
    private ExtraCardView newsCard;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mainView = inflater.inflate(R.layout.extras_fragment, container, false);
        maintainerCard = mainView.findViewById(R.id.maintainer_card);
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

    void updatePrefs(UpdateInfo update) {
        Log.d("ExtrasFragment:updatePrefs", "called");
        if (update == null) {
            Log.d("ExtrasFragment:updatePrefs", "update is null");
            mainView.setVisibility(View.GONE);
            return;
        }

        if (update.getMaintainer() != null && !update.getMaintainer().isEmpty()) {
            maintainerCard.setSummary(update.getMaintainer());
            if (update.getMaintainerUrl() != null && !update.getMaintainerUrl().isEmpty()) {
                maintainerCard.setOnClickListener(v -> openUrl(update.getMaintainerUrl()));
                maintainerCard.setClickable(true);
                maintainerCard.setVisibility(View.VISIBLE);
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