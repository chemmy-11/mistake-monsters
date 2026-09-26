// GitHub Release 创建 + APK 资产上传
// 用法: node gh-release.mjs <tag> <apkPath> <notesFile> <assetName>
import fs from "node:fs";

const TOKEN = fs.readFileSync("C:/Users/15266/.dsh/skills/.secrets/github-pat.txt", "utf8").trim();
const REPO = "chemmy-11/mistake-monsters";
const [tag, apkPath, notesPath, assetName] = process.argv.slice(2);
const notes = fs.readFileSync(notesPath, "utf8");
const h = { Authorization: `Bearer ${TOKEN}`, Accept: "application/vnd.github+json", "User-Agent": "release-script" };

let r = await fetch(`https://api.github.com/repos/${REPO}/releases`, {
  method: "POST",
  headers: { ...h, "Content-Type": "application/json" },
  body: JSON.stringify({ tag_name: tag, name: tag, body: notes, draft: false, prerelease: false }),
});
let j = await r.json();
if (!r.ok) {
  if ((j.message || "").includes("already_exists")) {
    const gr = await fetch(`https://api.github.com/repos/${REPO}/releases/tags/${tag}`, { headers: h });
    j = await gr.json();
  } else {
    console.error("create release failed:", r.status, j.message);
    process.exit(1);
  }
}
console.log("release:", j.id, j.html_url);

const buf = fs.readFileSync(apkPath);
const up = await fetch(
  `https://uploads.github.com/repos/${REPO}/releases/${j.id}/assets?name=${assetName}`,
  {
    method: "POST",
    headers: {
      Authorization: `Bearer ${TOKEN}`,
      "Content-Type": "application/octet-stream",
      "Content-Length": String(buf.length),
      "User-Agent": "release-script",
    },
    body: buf,
  },
);
const uj = await up.json();
console.log("asset:", up.status, uj.browser_download_url || uj.message);
if (!uj.browser_download_url) process.exit(1);
