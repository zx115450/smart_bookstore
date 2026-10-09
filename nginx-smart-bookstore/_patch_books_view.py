from pathlib import Path

# Insert ebook jump button before card default slot closes.
# Card whole-click goes to detail; button must stopPropagation.

OLD = (
    'a("div",ae,[a("span",oe,c(b(J)(e.price)),1),'
    '_(I,{size:"small",type:"info"},'
    '{default:v(()=>[x("可售 "+c(e.saleStock),1)]),_:2},1024)])])]),_:2},1032,["onClick"]'
)

# If Chinese garbled differently in file, locate by stable ASCII parts
ANCHOR_START = 'a("div",ae,[a("span",oe,c(b(J)(e.price)),1),'
ANCHOR_END = '])])]),_:2},1032,["onClick"]'

INSERT_BTN = (
    ',e.ebookId?(o(),r("button",{'
    'key:"eread",type:"button",class:"ebook-jump",'
    'onClick:ne=>{ne.stopPropagation();'
    'window.location.href="/reader.html?ebookId="+e.ebookId}'
    '},null,"读电子书")):B("",!0)'
)

CSS_SNIPPET = """
.book-card .ebook-jump{margin-top:8px;width:100%;border:0;border-radius:8px;padding:6px 10px;background:#0f5c4c;color:#fff;cursor:pointer;font:inherit;font-size:13px}
.book-card .ebook-jump:hover{filter:brightness(1.08)}
"""

paths = [
    Path(__file__).resolve().parent / "html" / "assets" / "BooksView-D80w84Qe.js",
    Path(r"F:\java_project\smart_bookstore\nginx-smart-bookstore\html\assets\BooksView-D80w84Qe.js"),
]
css_paths = [
    Path(__file__).resolve().parent / "html" / "assets" / "BooksView-PDmrahcs.css",
    Path(r"F:\java_project\smart_bookstore\nginx-smart-bookstore\html\assets\BooksView-PDmrahcs.css"),
]


def patch_js(path: Path) -> None:
    text = path.read_text(encoding="utf-8")
    if "ebook-jump" in text:
        print("js already", path)
        return
    start = text.find(ANCHOR_START)
    if start < 0:
        print("anchor start missing", path)
        return
    end = text.find(ANCHOR_END, start)
    if end < 0:
        print("anchor end missing", path)
        return
    # insert before the closing of ae div: ...1024)])  -> ...1024), BTN ])
    # Find the stock tag closing `},1024)` inside ae div
    mid = text.find("},1024)", start, end + len(ANCHOR_END))
    if mid < 0:
        print("tag close missing", path)
        return
    insert_at = mid + len("},1024)")
    # currently: },1024)])])  — first ] closes ae children array
    # we need: },1024), BTN ])])
    text2 = text[:insert_at] + INSERT_BTN + text[insert_at:]
    path.write_text(text2, encoding="utf-8")
    print("js patched", path)


def patch_css(path: Path) -> None:
    if not path.exists():
        print("css missing", path)
        return
    text = path.read_text(encoding="utf-8")
    if "ebook-jump" in text:
        print("css already", path)
        return
    path.write_text(text.rstrip() + "\n" + CSS_SNIPPET, encoding="utf-8")
    print("css patched", path)


for p in paths:
    if p.exists():
        patch_js(p)
    else:
        print("js missing", p)

for p in css_paths:
    patch_css(p)
