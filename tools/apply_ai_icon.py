"""Build the Xiaoji launcher artwork from the 1440 px AI source.

The source is full-bleed, so every output keeps its native pixels: no upscaling.
Only the provider watermark in the bottom-right corner is reconstructed and the
chick is keyed out for the adaptive foreground and the TV banner.
"""
from __future__ import annotations

from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

from generate_brand_assets import BRAND, RES, font, save

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / "docs/brand/source/icon-1440.png"
WATERMARK = (1220, 1320, 1440, 1440)


def gradient(size: tuple[int, int], top: tuple[int, int, int], bottom: tuple[int, int, int]) -> Image.Image:
    image = Image.new("RGBA", size)
    draw = ImageDraw.Draw(image)
    for y in range(size[1]):
        fraction = y / max(1, size[1] - 1)
        draw.line((0, y, size[0], y), fill=tuple(round(a + (b - a) * fraction) for a, b in zip(top, bottom)) + (255,))
    return image


def clean_art() -> Image.Image:
    """Reconstruct the provider watermark from the background above and left of it."""
    art = Image.open(ART).convert("RGBA")
    pixels = np.asarray(art).astype(np.float32)
    left, top, right, bottom = WATERMARK
    top_row = pixels[top - 1, left:right, :3]
    left_col = pixels[top:bottom, left - 1, :3]
    corner = top_row[0] + left_col[0] - pixels[top - 1, left - 1, :3]
    patch = top_row[None, :, :] + left_col[:, None, :] - corner[None, None, :]
    pixels[top:bottom, left:right, :3] = patch
    pixels[top:bottom, left:right, 3] = 255
    return Image.fromarray(np.clip(pixels, 0, 255).astype(np.uint8), "RGBA")


def key_chick(art: Image.Image) -> Image.Image:
    """Coverage matting: red channel against the emerald field, solid interior for eyes."""
    rgb = np.asarray(art.convert("RGB")).astype(np.float32)
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    high, low = rgb.max(axis=2), rgb.min(axis=2)
    keep = ((r > b + 35) & (r >= g - 15)) | (high < 80) | (low > 205)
    # Everything enclosed by the chick silhouette is solid: the eyes, the white catchlights.
    inverse = Image.fromarray((~keep * 255).astype(np.uint8), "L").copy()
    ImageDraw.floodfill(inverse, (0, 0), 128)
    holes = np.asarray(inverse) == 255
    filled = keep | holes
    interior = np.asarray(
        Image.fromarray((filled * 255).astype(np.uint8), "L").filter(ImageFilter.MinFilter(5))
    ) > 0
    coverage = np.clip((r - 16) / (235 - 16), 0, 1)
    coverage = np.clip((coverage - 0.35) / 0.65, 0, 1)  # drop the green-tinted blend ring
    alpha = np.where(filled, np.maximum(coverage, interior), 0)
    alpha_image = Image.fromarray((alpha * 255).astype(np.uint8), "L").filter(ImageFilter.GaussianBlur(0.8))
    out = art.copy()
    out.putalpha(alpha_image)
    box = alpha_image.getbbox()
    return out.crop(box) if box else out


def rounded(image: Image.Image, fraction: float) -> Image.Image:
    mask = Image.new("L", image.size)
    side = image.width
    ImageDraw.Draw(mask).rounded_rectangle((1, 1, side - 1, side - 1), radius=round(side * fraction), fill=255)
    out = image.copy()
    out.putalpha(mask)
    return out


def crisp(image: Image.Image, side: int) -> Image.Image:
    """Downscale to the target size, then restore edge contrast lost in resampling."""
    return image.resize((side, side), Image.Resampling.LANCZOS).filter(
        ImageFilter.UnsharpMask(radius=1.3, percent=55, threshold=3)
    )


def backdrop(size: int) -> Image.Image:
    image = gradient((size, size), (14, 236, 166), (8, 160, 140))
    glow = Image.new("RGBA", image.size)
    ImageDraw.Draw(glow).ellipse(
        (-size // 3, -size // 2, size * 2 // 3, size * 2 // 3), fill=(120, 255, 205, 34)
    )
    image.alpha_composite(glow.filter(ImageFilter.GaussianBlur(size // 9)))
    return image


def square_on(canvas: Image.Image, chick: Image.Image, fill: float) -> Image.Image:
    side = round(canvas.width * fill)
    scaled = chick.copy()
    scaled.thumbnail((side, side), Image.Resampling.LANCZOS)
    canvas.alpha_composite(scaled, ((canvas.width - scaled.width) // 2, (canvas.height - scaled.height) // 2))
    return canvas


def banner(chick: Image.Image) -> Image.Image:
    """Render at 2x and downscale once, so the Chinese text stays crisp."""
    width, height = 2560, 1440
    image = gradient((width, height), (11, 20, 28), (6, 11, 20))
    aura = Image.new("RGBA", image.size)
    ImageDraw.Draw(aura).ellipse((-400, -200, 1500, 1700), fill=(28, 165, 125, 34))
    image.alpha_composite(aura.filter(ImageFilter.GaussianBlur(220)))
    mark = chick.copy()
    mark.thumbnail((860, 860), Image.Resampling.LANCZOS)
    image.alpha_composite(mark, (150, (height - mark.height) // 2))
    draw = ImageDraw.Draw(image)
    draw.text((1178, 500), "小鸡影视", font=font(208, True), fill=(248, 249, 246, 255))
    draw.rounded_rectangle((1186, 806, 1458, 824), radius=9, fill=(99, 240, 186, 255))
    draw.text((1182, 914), "每一刻，都有好戏", font=font(80), fill=(168, 187, 189, 255))
    return image.resize((1280, 720), Image.Resampling.LANCZOS)


def main() -> None:
    art = clean_art()
    chick = key_chick(art)
    icon = rounded(crisp(art, 1024), 0.2)
    save(icon, BRAND / "xiaoji-icon.png")
    save(square_on(Image.new("RGBA", (1024, 1024)), chick, 0.76), BRAND / "xiaoji-foreground.png")
    save(backdrop(1024), BRAND / "xiaoji-background.png")

    save(square_on(Image.new("RGBA", (432, 432)), chick, 0.68), RES / "drawable-nodpi/ic_launcher_foreground.png")
    save(backdrop(432), RES / "drawable-nodpi/ic_launcher_background.png")
    save(icon.resize((256, 256), Image.Resampling.LANCZOS), RES / "drawable-nodpi/ic_brand_mark.png")
    for density, size in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
        save(icon.resize((size, size), Image.Resampling.LANCZOS), RES / f"mipmap-{density}/ic_launcher.png")
        save(rounded(icon, 0.5).resize((size, size), Image.Resampling.LANCZOS), RES / f"mipmap-{density}/ic_launcher_round.png")
    banner_image = banner(chick)
    save(banner_image, BRAND / "xiaoji-banner.png")
    save(banner_image, RES / "drawable-nodpi/app_banner.png")


if __name__ == "__main__":
    main()
