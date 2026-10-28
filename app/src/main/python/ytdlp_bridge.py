"""
Bridge between Kotlin (via Chaquopy) and yt-dlp.
All functions return JSON strings for easy parsing in Kotlin.
"""
import json
import os
import sys
import threading
import traceback

def _apply_ytdlp_update_path():
    try:
        from com.chaquo.python import Python
        context = Python.getPlatform().getApplication()
        files_dir = str(context.getFilesDir().getAbsolutePath())
        update_zip = os.path.join(files_dir, "updates", "yt-dlp.zip")
        if os.path.exists(update_zip):
            if update_zip in sys.path:
                sys.path.remove(update_zip)
            sys.path.insert(0, update_zip)
            return update_zip
    except Exception as e:
        print(f"Error checking for dynamic yt-dlp update: {e}", flush=True)
    return None

_apply_ytdlp_update_path()

try:
    import yt_dlp
except ImportError:
    yt_dlp = None

# Hook FFmpegPostProcessor for in-process JNI execution
_jni_ffmpeg_hooked = False

def _setup_jni_ffmpeg():
    global _jni_ffmpeg_hooked
    if _jni_ffmpeg_hooked:
        return True
    try:
        from com.ytdlp.ffmpeg import FFmpegBridge
    except Exception as e:
        print(f"FFmpegBridge JNI not accessible from Python: {e}")
        return False

    if not yt_dlp:
        return False

    try:
        from yt_dlp.postprocessor.ffmpeg import FFmpegPostProcessor, FFmpegPostProcessorError
        import itertools

        FFmpegPostProcessor.available = property(lambda self: FFmpegBridge.isAvailable())
        FFmpegPostProcessor.probe_available = property(lambda self: False)
        FFmpegPostProcessor._version = "9.0.2"
        FFmpegPostProcessor.basename = "ffmpeg"
        FFmpegPostProcessor.check_version = lambda self: None

        def jni_real_run_ffmpeg(self, input_path_opts, output_path_opts, *, expected_retcodes=(0,)):
            clean_paths = [
                path[5:] if (isinstance(path, str) and path.startswith('file:')) else str(path)
                for path, _ in input_path_opts if path
            ]
            oldest_mtime = min(
                (os.stat(p).st_mtime for p in clean_paths if os.path.exists(p)),
                default=None
            )

            cmd = ['-y', '-loglevel', 'repeat+info']

            def make_args(file, args, name, number):
                keys = [f'_{name}{number}', f'_{name}']
                if name == 'o':
                    args += ['-movflags', '+faststart']
                    if number == 1:
                        keys.append('')
                try:
                    args += self._configuration_args(self.basename, keys)
                except Exception:
                    pass
                if name == 'i':
                    args.append('-i')
                clean_file = file[5:] if (isinstance(file, str) and file.startswith('file:')) else str(file)
                return list(args) + [clean_file]

            for arg_type, path_opts in (('i', input_path_opts), ('o', output_path_opts)):
                cmd += itertools.chain.from_iterable(
                    make_args(path, list(opts), arg_type, i + 1)
                    for i, (path, opts) in enumerate(path_opts) if path)

            print(f"[JNI FFmpeg] Executing: {' '.join(cmd)}", flush=True)
            cmd_json = json.dumps(cmd)
            retcode = FFmpegBridge.runFFmpegJson(cmd_json)
            print(f"[JNI FFmpeg] Return code: {retcode}", flush=True)

            if retcode not in expected_retcodes:
                raise FFmpegPostProcessorError(f"FFmpeg JNI failed with exit code {retcode}")

            for out_path, _ in output_path_opts:
                if out_path and oldest_mtime is not None:
                    clean_out = out_path[5:] if (isinstance(out_path, str) and out_path.startswith('file:')) else str(out_path)
                    try:
                        self.try_utime(clean_out, oldest_mtime, oldest_mtime)
                    except Exception:
                        pass
            return ""

        def jni_get_audio_codec(self, path):
            clean_path = path[5:] if (isinstance(path, str) and path.startswith('file:')) else str(path)
            ext = os.path.splitext(clean_path)[1].lower().lstrip('.')
            ext_to_codec = {
                'm4a': 'aac',
                'aac': 'aac',
                'mp3': 'mp3',
                'opus': 'opus',
                'ogg': 'vorbis',
                'flac': 'flac',
                'wav': 'pcm_s16le',
                'webm': 'opus',
                'mp4': 'aac',
            }
            return ext_to_codec.get(ext, 'aac')

        FFmpegPostProcessor.get_audio_codec = jni_get_audio_codec
        FFmpegPostProcessor.real_run_ffmpeg = jni_real_run_ffmpeg
        _jni_ffmpeg_hooked = True
        print("[JNI FFmpeg] FFmpegPostProcessor hooked successfully to FFmpeg 9.0.2 JNI")
        return True
    except Exception as e:
        print(f"Error hooking FFmpegPostProcessor: {e}")
        return False

# Attempt hook at import time
_setup_jni_ffmpeg()

# Thread-safe progress state
_progress_data = {}
_progress_lock = threading.Lock()
_cancel_events = {}  # task_id -> threading.Event


def reload_ytdlp():
    """Reload yt_dlp module from dynamic update package if available."""
    global yt_dlp, _jni_ffmpeg_hooked
    try:
        update_path = _apply_ytdlp_update_path()
        for mod in list(sys.modules.keys()):
            if mod == 'yt_dlp' or mod.startswith('yt_dlp.'):
                sys.modules.pop(mod, None)
        import yt_dlp
        _jni_ffmpeg_hooked = False
        _setup_jni_ffmpeg()
        new_version = yt_dlp.version.__version__ if yt_dlp else "Unknown"
        print(f"[Dynamic Update] Successfully reloaded yt-dlp version: {new_version}", flush=True)
        return json.dumps({"status": "ok", "version": new_version, "path": update_path})
    except Exception as e:
        err = f"Failed to reload yt-dlp: {e}\n{traceback.format_exc()}"
        print(err, flush=True)
        return json.dumps({"status": "error", "error": err})


def is_available():
    """Check if yt-dlp module is loaded."""
    return json.dumps({"available": yt_dlp is not None, "version": yt_dlp.version.__version__ if yt_dlp else ""})


def get_version():
    """Return the currently installed yt-dlp version."""
    if yt_dlp:
        return yt_dlp.version.__version__
    return "Not installed"


def extract_info(url):
    """Fetch video metadata without downloading."""
    if not yt_dlp:
        return json.dumps({"status": "error", "error": "yt-dlp is not loaded"})

    ydl_opts = {
        'quiet': True,
        'no_warnings': True,
        'skip_download': True,
        'extract_flat': False,
    }
    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=False)
            if info is None:
                return json.dumps({"status": "error", "error": "Could not extract video information."})

            formats_list = _extract_format_list(info)
            subtitles_list = list(info.get("subtitles", {}).keys()) if info.get("subtitles") else []
            auto_subtitles_list = list(info.get("automatic_captions", {}).keys()) if info.get("automatic_captions") else []

            return json.dumps({
                "status": "ok",
                "title": info.get("title", "Unknown"),
                "duration": info.get("duration", 0) or 0,
                "uploader": info.get("uploader", "Unknown") or "Unknown",
                "thumbnail": info.get("thumbnail", "") or "",
                "webpage_url": info.get("webpage_url", url) or url,
                "formats": formats_list,
                "subtitles": subtitles_list,
                "auto_subtitles": auto_subtitles_list,
            })
    except Exception as e:
        return json.dumps({"status": "error", "error": str(e), "traceback": traceback.format_exc()})


def get_available_subtitles(url):
    """List available subtitle languages for a URL."""
    if not yt_dlp:
        return json.dumps({"status": "error", "error": "yt-dlp is not loaded"})

    ydl_opts = {'quiet': True, 'skip_download': True}
    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(url, download=False)
            if not info:
                return json.dumps({"status": "error", "error": "Could not extract info"})
            return json.dumps({
                "status": "ok",
                "manual": list(info.get("subtitles", {}).keys()) if info.get("subtitles") else [],
                "automatic": list(info.get("automatic_captions", {}).keys()) if info.get("automatic_captions") else [],
            })
    except Exception as e:
        return json.dumps({"status": "error", "error": str(e)})


def download(task_id, url, opts_json, ffmpeg_path=None):
    """Download media with specified options. Returns JSON result."""
    if not yt_dlp:
        return json.dumps({"status": "error", "task_id": task_id, "error": "yt-dlp is not loaded"})

    # Ensure in-process JNI FFmpeg hook is active
    _setup_jni_ffmpeg()

    try:
        opts = json.loads(opts_json) if isinstance(opts_json, str) else opts_json
    except Exception as e:
        return json.dumps({"status": "error", "task_id": task_id, "error": f"Invalid options JSON: {str(e)}"})

    cancel_event = threading.Event()
    _cancel_events[task_id] = cancel_event

    with _progress_lock:
        _progress_data[task_id] = {
            "status": "starting",
            "downloaded_bytes": 0,
            "total_bytes": 0,
            "speed": 0,
            "eta": 0,
            "filename": "",
        }

    output_dir = opts.get('output_dir', '.')
    os.makedirs(output_dir, exist_ok=True)

    outtmpl = os.path.join(output_dir, '%(title)s.%(ext)s')

    ydl_opts = {
        'outtmpl': outtmpl,
        'progress_hooks': [lambda d: _progress_hook(task_id, d)],
        'quiet': True,
        'no_warnings': True,
    }

    # Format specification
    # Prefer mp4 video + m4a audio to avoid WebM container issues with FFmpeg 3.3.2
    # (static binary used on Android 7/8 doesn't support WebM->MP4 stream copy)
    format_spec = opts.get('format')
    if format_spec:
        ydl_opts['format'] = format_spec
    else:
        ydl_opts['format'] = (
            'bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]'
            '/bestvideo[height<=720]+bestaudio'
            '/best[height<=720]'
        )

    # FFmpeg location (downloaded binary)
    if ffmpeg_path and os.path.exists(ffmpeg_path):
        ffmpeg_dir = os.path.dirname(ffmpeg_path)
        ydl_opts['ffmpeg_location'] = ffmpeg_dir
        # Reset yt-dlp's internal FFmpeg location cache so it picks up the new path
        try:
            from yt_dlp.postprocessor.ffmpeg import FFmpegPostProcessor
            FFmpegPostProcessor._ffmpeg_location = yt_dlp.utils.MISSING
        except Exception:
            pass
        # Pass -strict -2 to allow experimental codecs in old FFmpeg
        ydl_opts['postprocessor_args'] = {
            'ffmpeg': ['-strict', '-2']
        }

    # Merge format — always mp4 for maximum compatibility
    ydl_opts['merge_output_format'] = opts.get('merge_format', 'mp4')

    # Subtitles
    if opts.get('write_subtitles', False):
        ydl_opts['writesubtitles'] = True
        sub_langs = opts.get('subtitle_langs', ['en'])
        if isinstance(sub_langs, str):
            sub_langs = [sub_langs]
        ydl_opts['subtitleslangs'] = sub_langs
        ydl_opts['subtitlesformat'] = opts.get('subtitle_format', 'srt')

    if opts.get('auto_subtitles', False):
        ydl_opts['writeautomaticsub'] = True

    postprocessors = []

    if opts.get('embed_subtitles', False):
        postprocessors.append({
            'key': 'FFmpegEmbedSubtitle',
            'already_have_subtitle': False
        })

    # Audio-only extraction
    if opts.get('audio_only', False):
        audio_fmt = opts.get('audio_format', 'm4a')
        audio_qual = opts.get('audio_quality', '192')
        ydl_opts['format'] = 'bestaudio[ext=m4a]/bestaudio/best'
        postprocessors.append({
            'key': 'FFmpegExtractAudio',
            'preferredcodec': audio_fmt,
            'preferredquality': audio_qual,
        })

    # Lyrics / Metadata
    if opts.get('download_lyrics', False) or opts.get('add_metadata', False):
        postprocessors.append({
            'key': 'FFmpegMetadata',
            'add_metadata': True
        })

    if postprocessors:
        ydl_opts['postprocessors'] = postprocessors

    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            retcode = ydl.download([url])

        if cancel_event.is_set():
            return json.dumps({"status": "cancelled", "task_id": task_id})

        with _progress_lock:
            final_data = _progress_data.get(task_id, {})
            filename = final_data.get("filename", "")

        return json.dumps({
            "status": "complete",
            "task_id": task_id,
            "filename": filename,
            "returncode": retcode
        })
    except Exception as e:
        if cancel_event.is_set():
            return json.dumps({"status": "cancelled", "task_id": task_id})
        return json.dumps({"status": "error", "task_id": task_id, "error": str(e), "traceback": traceback.format_exc()})
    finally:
        _cancel_events.pop(task_id, None)


def get_progress(task_id):
    """Get current download progress for a task."""
    with _progress_lock:
        data = _progress_data.get(task_id, {
            "status": "unknown",
            "downloaded_bytes": 0,
            "total_bytes": 0,
            "speed": 0,
            "eta": 0,
            "filename": "",
        })
    return json.dumps(data)


def cancel_download(task_id):
    """Signal cancellation for a download task."""
    event = _cancel_events.get(task_id)
    if event:
        event.set()
        return json.dumps({"status": "cancelled"})
    return json.dumps({"status": "not_found"})


# --- Internal helpers ---

def _progress_hook(task_id, d):
    """Called by yt-dlp during download to report progress."""
    event = _cancel_events.get(task_id)
    if event and event.is_set():
        raise yt_dlp.utils.DownloadCancelled("Cancelled by user")

    status = d.get("status", "unknown")
    downloaded = d.get("downloaded_bytes", 0) or 0
    total = d.get("total_bytes") or d.get("total_bytes_estimate", 0) or 0
    speed = d.get("speed", 0) or 0
    eta = d.get("eta", 0) or 0
    filename = d.get("filename", "") or ""

    with _progress_lock:
        _progress_data[task_id] = {
            "status": status,
            "downloaded_bytes": downloaded,
            "total_bytes": total,
            "speed": speed,
            "eta": eta,
            "filename": filename,
        }


def _extract_format_list(info):
    """Extract a simplified format list from video info."""
    formats = info.get("formats", []) or []
    result = []
    seen = set()
    for f in formats:
        height = f.get("height")
        if height and height not in seen:
            seen.add(height)
            result.append({
                "height": height,
                "ext": f.get("ext", "mp4") or "mp4",
                "filesize": f.get("filesize") or f.get("filesize_approx", 0) or 0,
                "format_note": f.get("format_note", "") or "",
                "format_id": f.get("format_id", "") or "",
            })
    return sorted(result, key=lambda x: x["height"])
