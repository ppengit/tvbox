"""Render reproducible high-resolution artwork for the Xiaoji Video launcher."""
from __future__ import annotations

import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "app/src/main/res"
BRAND = ROOT / "docs/brand"
SIZE = 1024


def gradient(size: tuple[int, int], top: tuple[int, int, int], bottom: tuple[int, int, int]) -> Image.Image:
    image = Image.new("RGBA", size)
    draw = ImageDraw.Draw(image)
    for y in range(size[1]):
        fraction = y / max(1, size[1] - 1)
        color = tuple(round(a + (b - a) * fraction) for a, b in zip(top, bottom))
        draw.line((0, y, size[0], y), fill=color + (255,))
    return image


def curve(points: list[tuple[float, float]], steps: int = 60) -> list[tuple[float, float]]:
    p0, p1, p2, p3 = points
    return [tuple((1 - t) ** 3 * p0[i] + 3 * (1 - t) ** 2 * t * p1[i] + 3 * (1 - t) * t ** 2 * p2[i] + t ** 3 * p3[i] for i in (0, 1)) for t in (j / steps for j in range(steps + 1))]


def chick_mark(size: int = SIZE) -> Image.Image:
    output_size = size
    size *= 3
    image = Image.new("RGBA", (size, size))
    scale = size / 1024
    def box(values: tuple[float, ...]) -> tuple[int, ...]:
        return tuple(round(value * scale) for value in values)
    def pts(values: list[tuple[float, float]]) -> list[tuple[float, float]]:
        return [(x * scale, y * scale) for x, y in values]

    shadow = Image.new("RGBA", image.size)
    ImageDraw.Draw(shadow).ellipse(box((206, 320, 818, 852)), fill=(4, 48, 42, 130))
    image.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(26 * scale)))

    body = Image.new("L", image.size)
    ImageDraw.Draw(body).ellipse(box((196, 268, 828, 812)), fill=255)
    golden = gradient(image.size, (255, 236, 170), (243, 174, 62))
    image.alpha_composite(Image.composite(golden, Image.new("RGBA", image.size), body))

    highlight = Image.new("RGBA", image.size)
    ImageDraw.Draw(highlight).ellipse(box((268, 296, 566, 468)), fill=(255, 255, 255, 66))
    image.alpha_composite(Image.composite(highlight.filter(ImageFilter.GaussianBlur(30 * scale)), Image.new("RGBA", image.size), body))

    draw = ImageDraw.Draw(image)
    draw.ellipse(box((452, 226, 512, 296)), fill=(255, 230, 152, 255))
    draw.ellipse(box((514, 232, 574, 300)), fill=(249, 200, 99, 255))
    draw.ellipse(box((352, 444, 438, 550)), fill=(30, 40, 50, 255))
    draw.ellipse(box((586, 444, 672, 550)), fill=(30, 40, 50, 255))
    draw.ellipse(box((371, 462, 401, 494)), fill=(255, 255, 255, 235))
    draw.ellipse(box((605, 462, 635, 494)), fill=(255, 255, 255, 235))

    beak = [(470, 558), (470, 682), (602, 620)]
    draw.polygon(pts(beak), fill=(240, 122, 26, 255))
    radius = 13 * scale
    for x, y in pts(beak):
        draw.ellipse((x - radius, y - radius, x + radius, y + radius), fill=(240, 122, 26, 255))
    return image.resize((output_size, output_size), Image.Resampling.LANCZOS)


def backdrop(size: int = SIZE) -> Image.Image:
    image = gradient((size, size), (45, 205, 160), (7, 94, 84))
    light = Image.new("RGBA", image.size)
    draw = ImageDraw.Draw(light)
    draw.ellipse((-size // 4, -size // 3, size * 4 // 5, size * 4 // 5), fill=(140, 255, 210, 48))
    image.alpha_composite(light.filter(ImageFilter.GaussianBlur(size // 8)))
    return image


def regular_icon(size: int, rounded: bool = False) -> Image.Image:
    image = backdrop()
    mark = chick_mark().resize((1280, 1280), Image.Resampling.LANCZOS)
    image.alpha_composite(mark, (-128, -128))
    if rounded:
        mask = Image.new("L", image.size)
        ImageDraw.Draw(mask).ellipse((1, 1, SIZE - 1, SIZE - 1), fill=255)
        image.putalpha(mask)
    return image.resize((size, size), Image.Resampling.LANCZOS)


def save(image: Image.Image, path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    image.save(path, "PNG", optimize=True)
    print(f"{path.relative_to(ROOT)} ({image.width}x{image.height})")


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    filename = "msyhbd.ttc" if bold else "msyh.ttc"
    windows = Path("C:/Windows/Fonts") / filename
    if windows.exists():
        return ImageFont.truetype(str(windows), size)
    for file in (Path("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"), Path("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf")):
        if file.exists():
            return ImageFont.truetype(str(file), size)
    raise RuntimeError("A CJK font is required to render the Chinese TV banner")


def banner() -> Image.Image:
    image = gradient((1280, 720), (11, 20, 28), (6, 11, 20))
    aura = Image.new("RGBA", image.size)
    ImageDraw.Draw(aura).ellipse((-200, -100, 750, 850), fill=(28, 145, 115, 30))
    image.alpha_composite(aura.filter(ImageFilter.GaussianBlur(110)))
    image.alpha_composite(chick_mark(620), (-15, 45))
    draw = ImageDraw.Draw(image)
    draw.text((589, 250), "小鸡影视", font=font(104, True), fill=(248, 249, 246, 255))
    draw.rounded_rectangle((593, 403, 729, 412), radius=4, fill=(99, 240, 186, 255))
    draw.text((591, 457), "每一刻，都有好戏", font=font(40), fill=(168, 187, 189, 255))
    return image


def main() -> None:
    save(regular_icon(1024), BRAND / "xiaoji-icon.png")
    save(chick_mark(), BRAND / "xiaoji-foreground.png")
    save(backdrop(), BRAND / "xiaoji-background.png")
    save(chick_mark(432), RES / "drawable-nodpi/ic_launcher_foreground.png")
    save(backdrop(432), RES / "drawable-nodpi/ic_launcher_background.png")
    save(regular_icon(256), RES / "drawable-nodpi/ic_brand_mark.png")
    for density, size in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
        save(regular_icon(size), RES / f"mipmap-{density}/ic_launcher.png")
        save(regular_icon(size, True), RES / f"mipmap-{density}/ic_launcher_round.png")
    full_banner = banner()
    save(full_banner, BRAND / "xiaoji-banner.png")
    save(full_banner, RES / "drawable-nodpi/app_banner.png")


if __name__ == "__main__":
    main()
