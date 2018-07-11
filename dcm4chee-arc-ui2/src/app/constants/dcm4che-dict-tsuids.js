var DCM4CHE = DCM4CHE || {};
DCM4CHE.TransferSyntax = (function (dictionary) {
  var nameOf = function (uid) {
      if(uid === "all"){
          return dictionary;
      }
      return dictionary[uid] || uid;
  }
  return {
    nameOf:nameOf
  }
}({
"1.2.840.10008.1.2":"Implicit VR Little Endian",
"1.2.840.10008.1.2.1":"Explicit VR Little Endian",
"1.2.840.10008.1.2.1.99":"Deflated Explicit VR Little Endian",
"1.2.840.10008.1.2.2":"Explicit VR Big Endian (Retired)",
"1.2.840.10008.1.2.4.50":"JPEG Baseline (Process 1)",
"1.2.840.10008.1.2.4.51":"JPEG Extended (Process 2 & 4)",
"1.2.840.10008.1.2.4.52":"JPEG Extended (Process 3 & 5) (Retired)",
"1.2.840.10008.1.2.4.53":"JPEG Spectral Selection, Non-Hierarchical (Process 6 & 8) (Retired)",
"1.2.840.10008.1.2.4.54":"JPEG Spectral Selection, Non-Hierarchical (Process 7 & 9) (Retired)",
"1.2.840.10008.1.2.4.55":"JPEG Full Progression, Non-Hierarchical (Process 10 & 12) (Retired)",
"1.2.840.10008.1.2.4.56":"JPEG Full Progression, Non-Hierarchical (Process 11 & 13) (Retired)",
"1.2.840.10008.1.2.4.57":"JPEG Lossless, Non-Hierarchical (Process 14)",
"1.2.840.10008.1.2.4.58":"JPEG Lossless, Non-Hierarchical (Process 15) (Retired)",
"1.2.840.10008.1.2.4.59":"JPEG Extended, Hierarchical (Process 16 & 18) (Retired)",
"1.2.840.10008.1.2.4.60":"JPEG Extended, Hierarchical (Process 17 & 19) (Retired)",
"1.2.840.10008.1.2.4.61":"JPEG Spectral Selection, Hierarchical (Process 20 & 22) (Retired)",
"1.2.840.10008.1.2.4.62":"JPEG Spectral Selection, Hierarchical (Process 21 & 23) (Retired)",
"1.2.840.10008.1.2.4.63":"JPEG Full Progression, Hierarchical (Process 24 & 26) (Retired)",
"1.2.840.10008.1.2.4.64":"JPEG Full Progression, Hierarchical (Process 25 & 27) (Retired)",
"1.2.840.10008.1.2.4.65":"JPEG Lossless, Hierarchical (Process 28) (Retired)",
"1.2.840.10008.1.2.4.66":"JPEG Lossless, Hierarchical (Process 29) (Retired)",
"1.2.840.10008.1.2.4.70":"JPEG Lossless, Non-Hierarchical, First-Order Prediction (Process 14 [Selection Value 1])",
"1.2.840.10008.1.2.4.80":"JPEG-LS Lossless Image Compression",
"1.2.840.10008.1.2.4.81":"JPEG-LS Lossy (Near-Lossless) Image Compression",
"1.2.840.10008.1.2.4.90":"JPEG 2000 Image Compression (Lossless Only)",
"1.2.840.10008.1.2.4.91":"JPEG 2000 Image Compression",
"1.2.840.10008.1.2.4.92":"JPEG 2000 Part 2 Multi-component Image Compression (Lossless Only)",
"1.2.840.10008.1.2.4.93":"JPEG 2000 Part 2 Multi-component Image Compression",
"1.2.840.10008.1.2.4.94":"JPIP Referenced",
"1.2.840.10008.1.2.4.95":"JPIP Referenced Deflate",
"1.2.840.10008.1.2.4.100":"MPEG2 Main Profile / Main Level",
"1.2.840.10008.1.2.4.101":"MPEG2 Main Profile / High Level",
"1.2.840.10008.1.2.4.102":"MPEG-4 AVC/H.264 High Profile / Level 4.1",
"1.2.840.10008.1.2.4.103":"MPEG-4 AVC/H.264 BD-compatible High Profile / Level 4.1",
"1.2.840.10008.1.2.4.104":"MPEG-4 AVC/H.264 High Profile / Level 4.2 For 2D Video",
"1.2.840.10008.1.2.4.105":"MPEG-4 AVC/H.264 High Profile / Level 4.2 For 3D Video",
"1.2.840.10008.1.2.4.106":"MPEG-4 AVC/H.264 Stereo High Profile / Level 4.2",
"1.2.840.10008.1.2.4.107":"HEVC/H.265 Main Profile / Level 5.1",
"1.2.840.10008.1.2.4.108":"HEVC/H.265 Main 10 Profile / Level 5.1",
"1.2.840.10008.1.2.5":"RLE Lossless",
"1.2.840.10008.1.2.6.1":"RFC 2557 MIME encapsulation (Retired)",
"1.2.840.10008.1.2.6.2":"XML Encoding (Retired)",
"1.2.840.10008.1.20":"Papyrus 3 Implicit VR Little Endian (Retired)"
}));
