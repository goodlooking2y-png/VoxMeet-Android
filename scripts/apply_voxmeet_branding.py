from pathlib import Path
import xml.etree.ElementTree as ET

root = Path("VoxMeet/app/src/main")
manifest = root / "AndroidManifest.xml"
if not manifest.exists():
    raise SystemExit("AndroidManifest.xml not found")
ET.register_namespace("android", "http://schemas.android.com/apk/res/android")
tree = ET.parse(manifest)
app = tree.getroot().find("application")
if app is None:
    raise SystemExit("application element not found in manifest")
ns = "{http://schemas.android.com/apk/res/android}"
app.set(ns + "label", "@string/app_name")
app.set(ns + "icon", "@mipmap/ic_launcher")
app.set(ns + "roundIcon", "@mipmap/ic_launcher")
tree.write(manifest, encoding="utf-8", xml_declaration=True)

values = root / "res/values"
values.mkdir(parents=True, exist_ok=True)
strings = values / "strings.xml"
if strings.exists():
    st = ET.parse(strings)
    sr = st.getroot()
else:
    sr = ET.Element("resources")
for node in list(sr):
    if node.tag == "string" and node.get("name") == "app_name":
        sr.remove(node)
ET.SubElement(sr, "string", {"name":"app_name"}).text = "VoxMeet"
ET.ElementTree(sr).write(strings, encoding="utf-8", xml_declaration=True)

res = root / "res"
(res / "drawable").mkdir(parents=True, exist_ok=True)
(res / "mipmap-anydpi").mkdir(parents=True, exist_ok=True)
(res / "mipmap-anydpi-v26").mkdir(parents=True, exist_ok=True)
foreground = """<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp" android:viewportWidth="512" android:viewportHeight="512">
    <path android:fillColor="#071A56" android:pathData="M0,0h512v512h-512z"/>
    <path android:fillColor="#0B55C9" android:pathData="M0,0h256v512h-256z"/>
    <path android:fillColor="#6124B8" android:pathData="M256,0h256v512h-256z"/>
    <path android:fillColor="#11112D" android:pathData="M42,72 Q112,18 194,64 Q228,94 216,194 L204,262 Q190,310 130,310 Q70,310 54,260 L42,194 Z"/>
    <path android:fillColor="#F2B18E" android:pathData="M75,120 Q125,80 170,112 L178,207 Q172,270 126,270 Q82,270 74,211 Z"/>
    <path android:fillColor="#17102D" android:pathData="M294,72 Q380,16 466,78 L454,212 Q446,270 390,270 Q330,270 310,216 Z"/>
    <path android:fillColor="#F2B18E" android:pathData="M330,122 Q385,82 436,122 L432,212 Q422,270 380,270 Q338,270 326,212 Z"/>
    <path android:fillColor="#0B1A70" android:pathData="M18,270 Q100,226 250,290 L250,448 L18,448 Z"/>
    <path android:fillColor="#5717A8" android:pathData="M262,290 Q390,226 494,270 L494,448 L262,448 Z"/>
    <path android:fillColor="#14D9F4" android:pathData="M52,310h20v84h-20z M88,286h18v132h-18z M124,320h16v66h-16z M372,320h16v66h-16z M406,286h18v132h-18z M440,310h20v84h-20z"/>
    <path android:fillColor="#FFFFFF" android:pathData="M228,162a28,28 0,0 1,56 0v112a28,28 0,0 1,-56 0z M212,254h16v20a28,28 0,0 0,56 0v-20h16v20a44,44 0,0 1,-36 43v34h24v16h-64v-16h24v-34a44,44 0,0 1,-36 -43z"/>
    <path android:fillColor="#FFFFFF" android:pathData="M148,454h216v18h-216z"/>
</vector>"""
(res / "drawable/ic_launcher_foreground.xml").write_text(foreground)
adaptive = """<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/voxmeet_icon_background"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>"""
(res / "mipmap-anydpi-v26/ic_launcher.xml").write_text(adaptive)
(res / "mipmap-anydpi/ic_launcher.xml").write_text(foreground)
colors = values / "colors.xml"
if colors.exists():
    ct = ET.parse(colors); cr = ct.getroot()
else:
    cr = ET.Element("resources")
for node in list(cr):
    if node.tag == "color" and node.get("name") == "voxmeet_icon_background":
        cr.remove(node)
ET.SubElement(cr, "color", {"name":"voxmeet_icon_background"}).text = "#120B2D"
ET.ElementTree(cr).write(colors, encoding="utf-8", xml_declaration=True)
print("Applied VoxMeet display name and launcher/adaptive icon resources.")
