# PureNexus OTA
### Based on Lineage/CyanogenMod ota app

## How to use?
- Add this to our vendor:
```
# Essential OTA Config
PRODUCT_PACKAGES += \
    PureOTA

PRODUCT_PROPERTY_OVERRIDES += \
    ro.ota.build.date=$(shell date +%Y%m%d)

# Device specific
PRODUCT_PROPERTY_OVERRIDES += \
ro.ota.manifest=https://raw.githubusercontent.com/PureNexusProject-Mod/OTA_server/master/device_name.json
```
# Libraries and thanks
- MarkdownView-Android by [@mukeshsolanki](https://github.com/mukeshsolanki) (https://github.com/mukeshsolanki/MarkdownView-Android)
- RootTools by [@Stericson](https://github.com/Stericson)
(https://github.com/Stericson/RootTools)
- Apache Commons IO
(https://commons.apache.org/proper/commons-io/download_io.cgi)
- DragListView by [@woxblom](https://github.com/woxblom) (https://github.com/woxblom/DragListView/)
- FloatingActionMenu by [@Clans](https://github.com/Clans) (https://github.com/Clans/FloatingActionButton)
- Thanks also to [@MatthewBooth](https://github.com/MatthewBooth) for the helpful installation functions
