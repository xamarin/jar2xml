all: maps.xml

maps/maps.xml:
	mkdir -p maps
	java -jar jar2xml.jar --jar=$(ANDROID_SDK_PATH)/add-ons/addon_google_apis_google_inc_10/libs/maps.jar  --out=maps/maps.xml --ref=$(ANDROID_SDK_PATH)/platforms/android-10/android.jar 

