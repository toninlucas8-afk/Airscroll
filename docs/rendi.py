import asyncio, pathlib
from playwright.async_api import async_playwright

BASE = pathlib.Path(__file__).parent

PIEDE = """
<div style="font-family:'DejaVu Sans',Arial,sans-serif;font-size:7.5pt;color:#5a6b62;
     width:100%;padding:0 18mm;display:flex;justify-content:space-between;">
  <span>TITOLO</span><span class="pageNumber"></span>
</div>"""

async def main():
    async with async_playwright() as p:
        browser = await p.chromium.launch(executable_path="/opt/pw-browsers/chromium-1194/chrome-linux/chrome")
        page = await browser.new_page()
        for nome, titolo in [("manuale", "AirScroll — Manuale d'uso"),
                             ("privacy", "AirScroll — Informativa privacy")]:
            await page.goto((BASE / f"{nome}.html").as_uri(), wait_until="load")
            await page.pdf(
                path=str(BASE / f"AirScroll-{nome}.pdf"),
                format="A4",
                print_background=True,
                display_header_footer=True,
                header_template="<div></div>",
                footer_template=PIEDE.replace("TITOLO", titolo),
                margin={"top": "16mm", "bottom": "16mm", "left": "0", "right": "0"},
            )
            print("creato:", f"AirScroll-{nome}.pdf")
        await browser.close()

asyncio.run(main())
