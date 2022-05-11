Updater
=======
Simple application to download and apply OTA packages, based on LineageOS app.

Build with Android Studio
-------------------------
Updater needs access to the system API, therefore it can't be built only using
the public SDK. You first need to generate the libraries with all the needed
classes. The application also needs elevated privileges, so you need to sign
it with the right key to update the one in the system partition. To do this:

 - Place this directory anywhere in the Android source tree
 - Generate a keystore and keystore.properties using `gen-keystore.sh`
 - Build the dependencies by running `m framework` from the root of the
   Android source tree.
 - Create `system_libs/` directory in Updates repo and copy `classes.jar` file
   from `out/target/common/obj/JAVA_LIBRARIES/framework_intermediates/` into it.

You need to do the above once, unless Android Studio can't find some symbol.
In this case, rebuild the system libraries with `m framework`.
