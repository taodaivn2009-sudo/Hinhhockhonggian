import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const sourcePdf = path.resolve(__dirname, '../../app/src/main/assets/docs/toan_10_11_12_dpad.pdf');
const targetDir = path.resolve(__dirname, '../public/docs');
const targetPdf = path.join(targetDir, 'toan_10_11_12_dpad.pdf');
const CLOUDFLARE_LIMIT_BYTES = 24.5 * 1024 * 1024; // Cloudflare Pages 25 MiB single-file limit

if (fs.existsSync(sourcePdf)) {
  const stat = fs.statSync(sourcePdf);
  if (stat.size <= CLOUDFLARE_LIMIT_BYTES) {
    fs.mkdirSync(targetDir, { recursive: true });
    fs.copyFileSync(sourcePdf, targetPdf);
    console.log(`[prepare-pdf] Copied bundled PDF (${(stat.size / 1024 / 1024).toFixed(2)} MB) to public/docs/`);
  } else {
    if (fs.existsSync(targetPdf)) fs.unlinkSync(targetPdf);
    console.warn(
      `[prepare-pdf] PDF is ${(stat.size / 1024 / 1024).toFixed(2)} MB (> 25 MB Cloudflare Pages limit). ` +
      'Skipped bundling into dist/. Upload it to Cloudflare R2 and set VITE_PDF_URL in Cloudflare Pages settings.'
    );
  }
} else {
  console.log('[prepare-pdf] Bundled Android PDF not found; using VITE_PDF_URL or local file upload.');
}
