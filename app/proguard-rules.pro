-keep class cn.kylebing.bbq10wubi.WubiApp { *; }
-keep class cn.kylebing.bbq10wubi.Prefs { *; }
-keep class cn.kylebing.bbq10wubi.ui.** { *; }
-keep class cn.kylebing.bbq10wubi.sync.** { *; }
-keep class cn.kylebing.bbq10wubi.engine.** { *; }
-keep class cn.kylebing.bbq10wubi.data.** { *; }

-keep class cn.kylebing.bbq10wubi.internal.bc.tls.crypto.impl.bc.** { *; }
-keep class cn.kylebing.bbq10wubi.internal.bc.crypto.engines.** { *; }
-keep class cn.kylebing.bbq10wubi.internal.bc.crypto.modes.** { *; }
-keep class cn.kylebing.bbq10wubi.internal.bc.crypto.macs.** { *; }
-keep class cn.kylebing.bbq10wubi.internal.bc.crypto.agreement.** { *; }

-dontwarn cn.kylebing.bbq10wubi.internal.bc.**
-dontwarn javax.naming.**
-dontwarn javax.servlet.**
-dontwarn org.apache.**
