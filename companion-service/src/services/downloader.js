import { execFile } from 'child_process';
import { promisify } from 'util';
import path from 'path';
import fs from 'fs';

const execFileAsync = promisify(execFile);

export async function downloadAsWav(videoId, downloadDir) {
    const outputPath = path.join(downloadDir, `${videoId}.wav`);

    // Return immediately if already downloaded
    if (fs.existsSync(outputPath)) {
        return outputPath;
    }

    await execFileAsync('yt-dlp', [
        '-x',
        '--audio-format', 'wav',
        '-o', outputPath,
        '--no-playlist',
        `https://www.youtube.com/watch?v=${videoId}`
    ], { timeout: 120000 });

    return outputPath;
}

export function deleteDownload(videoId, downloadDir) {
    const filePath = path.join(downloadDir, `${videoId}.wav`);
    if (fs.existsSync(filePath)) {
        fs.unlinkSync(filePath);
        return true;
    }
    return false;
}
