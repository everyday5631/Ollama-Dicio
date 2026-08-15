"""Resolve Ollama model references into direct GGUF download URLs.

This is the desktop counterpart of ``OllamaRegistry.kt`` in the Android app, and it
speaks to the same registry in the same way.

Ollama's registry is a stock OCI distribution API served anonymously, with no token
exchange, so ``ollama pull`` reduces to two plain GETs:

1. ``GET https://<host>/v2/<namespace>/<name>/manifests/<tag>`` returns a manifest
   whose ``layers`` describe the parts of the model.
2. The layer with media type ``application/vnd.ollama.image.model`` *is the GGUF
   file itself*, byte for byte -- it needs no unwrapping -- and is fetched from
   ``GET https://<host>/v2/<namespace>/<name>/blobs/<digest>``.

Blob responses honour HTTP range requests, which :mod:`.model_store` uses to resume
interrupted downloads.
"""

from __future__ import annotations

import json
import logging
import urllib.request
from dataclasses import dataclass

log = logging.getLogger(__name__)

DEFAULT_HOST = "registry.ollama.ai"
DEFAULT_NAMESPACE = "library"
DEFAULT_TAG = "latest"

MEDIA_TYPE_MODEL = "application/vnd.ollama.image.model"
MEDIA_TYPE_TEMPLATE = "application/vnd.ollama.image.template"

_ACCEPT_MANIFEST = "application/vnd.docker.distribution.manifest.v2+json, application/json"


@dataclass(frozen=True)
class Reference:
    """A model reference broken into its parts."""

    host: str
    namespace: str
    name: str
    tag: str

    @property
    def manifest_url(self) -> str:
        return f"https://{self.host}/v2/{self.namespace}/{self.name}/manifests/{self.tag}"

    def blob_url(self, digest: str) -> str:
        return f"https://{self.host}/v2/{self.namespace}/{self.name}/blobs/{digest}"

    def __str__(self) -> str:
        return f"{self.host}/{self.namespace}/{self.name}:{self.tag}"


@dataclass(frozen=True)
class Resolved:
    """The outcome of resolving a reference against the registry."""

    blob_url: str
    size_bytes: int
    template: str | None


def is_reference(text: str) -> bool:
    """Whether *text* is an Ollama reference rather than a plain download URL."""
    t = text.strip()
    return bool(t) and not t.startswith(("http://", "https://"))


def parse(text: str) -> Reference:
    """Split ``[host/][namespace/]name[:tag]`` into its parts, applying Ollama's defaults.

    A leading component is only treated as a host when it looks like one, so that
    ``myuser/mymodel`` reads as a namespace rather than a host, while ``hf.co/user/repo``
    and ``localhost:11434/...`` behave as expected.
    """
    rest = text.strip().rstrip("/")
    if not rest:
        raise ValueError("Empty model reference")

    # the tag is separated by the last ':', but only when it comes after the last '/',
    # so a port in the host is not mistaken for a tag
    tag = DEFAULT_TAG
    colon = rest.rfind(":")
    if colon > rest.rfind("/"):
        tag = rest[colon + 1:] or DEFAULT_TAG
        rest = rest[:colon]

    parts = [p for p in rest.split("/") if p]
    if not parts:
        raise ValueError(f"Could not parse model reference: {text!r}")
    if len(parts) == 1:
        return Reference(DEFAULT_HOST, DEFAULT_NAMESPACE, parts[0], tag)

    first = parts[0]
    if "." in first or ":" in first or first == "localhost":
        namespace = "/".join(parts[1:-1]) or DEFAULT_NAMESPACE
        return Reference(first, namespace, parts[-1], tag)
    return Reference(DEFAULT_HOST, "/".join(parts[:-1]), parts[-1], tag)


def _get(url: str, accept: str, timeout: float = 30.0) -> bytes:
    request = urllib.request.Request(url, headers={"Accept": accept})
    with urllib.request.urlopen(request, timeout=timeout) as response:
        return response.read()


def resolve(text: str, timeout: float = 30.0) -> Resolved:
    """Fetch the manifest for *text* and return the location of its GGUF layer.

    Raises :class:`OSError` if the registry is unreachable and :class:`ValueError` if the
    manifest carries no model layer.
    """
    ref = parse(text)
    log.info("Resolving %s via %s", ref, ref.manifest_url)

    manifest = json.loads(_get(ref.manifest_url, _ACCEPT_MANIFEST, timeout))
    layers = manifest.get("layers")
    if not layers:
        raise ValueError(f"Manifest for {ref} has no layers")

    digest: str | None = None
    size = 0
    template_digest: str | None = None
    for layer in layers:
        media_type = layer.get("mediaType")
        if media_type == MEDIA_TYPE_MODEL:
            digest = layer.get("digest")
            size = int(layer.get("size", 0))
        elif media_type == MEDIA_TYPE_TEMPLATE:
            template_digest = layer.get("digest")

    if not digest:
        raise ValueError(f"Manifest for {ref} contains no {MEDIA_TYPE_MODEL} layer")

    # the template is a nicety: failing to fetch it must not block the download
    template = None
    if template_digest:
        try:
            template = _get(ref.blob_url(template_digest), "*/*", timeout).decode("utf-8")
        except Exception:
            log.warning("Could not fetch prompt template for %s", ref, exc_info=True)

    return Resolved(blob_url=ref.blob_url(digest), size_bytes=size, template=template)
