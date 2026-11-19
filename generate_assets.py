import os
from PIL import Image, ImageDraw, ImageFont

res_dir = r"D:\godlama\Saf Ekran\res"

sizes = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

def draw_icon(size):
    # Pure black background (#000000)
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(img)

    # Outer rounded square (app background)
    pad = int(size * 0.04)
    radius = int(size * 0.22)
    draw.rounded_rectangle([pad, pad, size - pad, size - pad], radius=radius, fill=(0, 0, 0, 255), outline=(32, 32, 32, 255), width=max(1, int(size * 0.02)))

    # 2x2 Grid of Minimalist Tiles
    # Grid area
    grid_margin = int(size * 0.22)
    grid_size = size - (2 * grid_margin)
    gap = int(size * 0.06)
    tile_w = (grid_size - gap) // 2
    tile_h = tile_w
    tile_r = max(2, int(tile_w * 0.22))

    # Top-Left Tile (Hero Active Tile: Pure White with subtle inner glow)
    x0 = grid_margin
    y0 = grid_margin
    draw.rounded_rectangle([x0, y0, x0 + tile_w, y0 + tile_h], radius=tile_r, fill=(255, 255, 255, 255))

    # Top-Right Tile (Sleek Dark Surface)
    x1 = x0 + tile_w + gap
    y1 = y0
    draw.rounded_rectangle([x1, y1, x1 + tile_w, y1 + tile_h], radius=tile_r, fill=(26, 26, 26, 255), outline=(70, 70, 70, 255), width=max(1, int(size * 0.015)))

    # Bottom-Left Tile (Sleek Dark Surface)
    x2 = x0
    y2 = y0 + tile_h + gap
    draw.rounded_rectangle([x2, y2, x2 + tile_w, y2 + tile_h], radius=tile_r, fill=(26, 26, 26, 255), outline=(70, 70, 70, 255), width=max(1, int(size * 0.015)))

    # Bottom-Right Tile (Spacer Tile: Subtle Dashed/Transparent with soft highlight)
    x3 = x1
    y3 = y2
    draw.rounded_rectangle([x3, y3, x3 + tile_w, y3 + tile_h], radius=tile_r, fill=(12, 12, 12, 255), outline=(130, 130, 130, 255), width=max(1, int(size * 0.015)))

    return img

# Generate all mipmap icons
for folder, size in sizes.items():
    out_folder = os.path.join(res_dir, folder)
    os.makedirs(out_folder, exist_ok=True)
    icon_path = os.path.join(out_folder, "ic_launcher.png")
    img = draw_icon(size)
    img.save(icon_path, "PNG")
    print(f"[*] Icon saved: {icon_path} ({size}x{size})")

# Generate TV Leanback Banner (320x180)
banner_w = 320
banner_h = 180
banner = Image.new("RGBA", (banner_w, banner_h), (0, 0, 0, 255))
b_draw = ImageDraw.Draw(banner)

# Subtle border
b_draw.rectangle([0, 0, banner_w - 1, banner_h - 1], outline=(30, 30, 30, 255), width=1)

# Draw icon emblem on the left
icon_emblem = draw_icon(120)
banner.paste(icon_emblem, (24, 30), icon_emblem)

# Try to use standard font or default font
font_large = None
font_small = None
try:
    font_large = ImageFont.truetype("arialbd.ttf", 26)
    font_small = ImageFont.truetype("arial.ttf", 13)
except Exception:
    try:
        font_large = ImageFont.truetype("C:\\Windows\\Fonts\\arialbd.ttf", 26)
        font_small = ImageFont.truetype("C:\\Windows\\Fonts\\arial.ttf", 13)
    except Exception:
        font_large = ImageFont.load_default()
        font_small = ImageFont.load_default()

# Text: SAF EKRAN
b_draw.text((160, 55), "SAF EKRAN", fill=(255, 255, 255, 255), font=font_large)
b_draw.text((162, 92), "Mİnİmalİst TV Başlatıcı", fill=(160, 160, 160, 255), font=font_small)
b_draw.text((162, 114), "Vezir • Saf Siyah • Serbest Izgara", fill=(90, 90, 90, 255), font=font_small)

banner_path = os.path.join(res_dir, "drawable", "banner.png")
banner.save(banner_path, "PNG")
print(f"[*] Banner saved: {banner_path} (320x180)")
