all: maps/Mono.Android.GoogleMaps.dll

maps/maps.xml:
	mkdir -p maps
	java -jar jar2xml.jar --jar=$(ANDROID_SDK_PATH)/add-ons/addon_google_apis_google_inc_10/libs/maps.jar  --out=maps/maps.xml --ref=$(ANDROID_SDK_PATH)/platforms/android-10/android.jar 

maps/Mono.Android.GoogleMaps.dll: maps/maps.xml
	mono --debug $(MONO_ANDROID_PATH)/lib/mandroid/generator.exe --csdir=maps/src/generated --javadir=maps/java/generated --assembly="Mono.Android.GoogleMaps, Version=0.0.0.0, Culture=neutral, PublicKeyToken=null" maps/maps.xml
