"""Publish a Paper JAR to Hangar with the configured platform versions."""

import argparse
import json
import os
from pathlib import Path
import sys
from urllib.error import HTTPError, URLError
from urllib.parse import quote
from urllib.request import Request, urlopen
from uuid import uuid4


API_BASE = "https://hangar.papermc.io/api/v1"


def send(url, *, method="GET", token=None, body=None, content_type=None):
    headers = {"User-Agent": "underilla-hangar-publish"}
    if token:
        headers["Authorization"] = token
    if content_type:
        headers["Content-Type"] = content_type
    request = Request(url, data=body, headers=headers, method=method)
    try:
        with urlopen(request, timeout=120) as response:
            return response.status, response.read()
    except HTTPError as error:
        return error.code, error.read()
    except URLError as error:
        raise RuntimeError(f"Hangar request failed: {error.reason}") from error


def multipart_upload(jar, metadata):
    boundary = f"underilla-{uuid4().hex}"
    payload = json.dumps(metadata).encode("utf-8")
    body = b"".join((
        f'--{boundary}\r\nContent-Disposition: form-data; name="files"; filename="{jar.name}"\r\n'
        "Content-Type: application/java-archive\r\n\r\n".encode(),
        jar.read_bytes(),
        f'\r\n--{boundary}\r\nContent-Disposition: form-data; name="versionUpload"\r\n'
        "Content-Type: application/json\r\n\r\n".encode(),
        payload,
        f"\r\n--{boundary}--\r\n".encode(),
    ))
    return body, f"multipart/form-data; boundary={boundary}"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--slug", required=True)
    parser.add_argument("--version", required=True)
    parser.add_argument("--channel", default="Release")
    parser.add_argument("--description", required=True)
    parser.add_argument("--paper-versions", required=True)
    parser.add_argument("--file", type=Path, required=True)
    args = parser.parse_args()

    api_key = os.environ.get("HANGAR_API_TOKEN")
    if not api_key:
        parser.error("HANGAR_API_TOKEN is required")
    paper_versions = [part.strip() for part in args.paper_versions.split(",")]
    if not all(paper_versions):
        parser.error("Paper versions must be nonempty comma-separated values")
    if not args.file.is_file():
        parser.error(f"JAR does not exist: {args.file}")

    status, response = send(
        f"{API_BASE}/authenticate?apiKey={quote(api_key, safe='')}", method="POST", body=b""
    )
    if not 200 <= status < 300:
        raise RuntimeError(f"Hangar authentication failed: HTTP {status}")
    token = json.loads(response).get("token")
    if not token:
        raise RuntimeError("Hangar authentication returned no token")

    project = quote(args.slug, safe="")
    version = quote(args.version, safe="")
    version_url = f"{API_BASE}/projects/{project}/versions/{version}"
    status, _ = send(version_url, token=token)
    if 200 <= status < 300:
        print(f"Already published to Hangar: {version_url}")
        return
    if status != 404:
        raise RuntimeError(f"Could not check Hangar version: HTTP {status}")

    metadata = {
        "version": args.version,
        "channel": args.channel,
        "description": args.description,
        "files": [{"platforms": ["PAPER"]}],
        "pluginDependencies": {},
        "platformDependencies": {"PAPER": paper_versions},
    }
    body, content_type = multipart_upload(args.file, metadata)
    status, response = send(
        f"{API_BASE}/projects/{project}/upload",
        method="POST", token=token, body=body, content_type=content_type,
    )
    if not 200 <= status < 300:
        raise RuntimeError(f"Hangar upload failed: HTTP {status}: {response.decode('utf-8', errors='replace')}")
    print(f"Published to Hangar: {json.loads(response)['url']}")


if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, RuntimeError) as error:
        sys.exit(str(error))
