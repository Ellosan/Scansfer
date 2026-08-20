# ZXing reflectively references encoder/decoder helpers.
-keep class com.google.zxing.** { *; }

# ML Kit barcode scanning models.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_barcode.** { *; }
