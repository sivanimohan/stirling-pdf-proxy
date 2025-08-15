
from fastapi import FastAPI, UploadFile, File
import requests
import google.generativeai as genai
from PyPDF2 import PdfReader
import tempfile
import os
from dotenv import load_dotenv

load_dotenv()
GEMINI_API_KEY = os.environ.get("GEMINI_API_KEY")
genai.configure(api_key=GEMINI_API_KEY)
# print(genai.list_models())

app = FastAPI()

@app.post("/process-pdf")
async def process_pdf(file: UploadFile = File(...)):
    # Save uploaded PDF to a temp file
    with tempfile.NamedTemporaryFile(delete=False, suffix=".pdf") as tmp:
        tmp.write(await file.read())
        tmp_path = tmp.name

    # Extract first 15 pages' text
    reader = PdfReader(tmp_path)
    num_pages = min(15, len(reader.pages))
    extracted_text = "\n".join([reader.pages[i].extract_text() or "" for i in range(num_pages)])

    # Get Table of Contents from Gemini
    genai.configure(api_key="AIzaSyCBLOSPwmCnLXHAQekX6DnSp-OrQdINpyU")
    model = genai.GenerativeModel("gemini-2.5-pro")
    toc_prompt = (
        "You are an expert in book structure extraction. "
        "Given the text from the first 15 pages of a book, extract a detailed and accurate table of contents. "
        "Include all chapters, sections, and subsections with their titles and page numbers if available. "
        "Format the output as a clear, structured list.\n"
        f"Text:\n{extracted_text}"
    )
    toc_result = model.generate_content(toc_prompt)
    toc = toc_result.text


    import json
    import os
    # Send PDF to Java endpoint for chapter headings
    with open(tmp_path, "rb") as pdf_file:
        java_url = "http://localhost:8080/get/pdf-info/detect-chapter-headings"
        response = requests.post(java_url, files={"file": (file.filename, pdf_file, file.content_type)})
        headings = response.json()

    # Save detected headings to JSON file
    detected_path = os.path.join(os.path.dirname(__file__), "..", "detected_headings.json")
    with open(detected_path, "w", encoding="utf-8") as f:
        json.dump(headings, f, ensure_ascii=False, indent=2)

    # --- Gemini Prompt 1: Extract book title, authors, and TOC ---
    prompt1 = (
        "The following text is from the first 15 pages of a book's PDF. Your only task is to find the name of the book, the authors, and then the Table of Contents within this text, ignoring everything else.\n"
        "Analyze the text, extract:\n"
        "1. The book title\n"
        "2. The authors\n"
        "3. The complete chapter list with titles\n"
        "Return the results as a single JSON object with this exact format:\n"
        "{\n  'book_title': 'Exact Book Title',\n  'authors': ['Author One', 'Author Two'],\n  'toc': [\n    {'chapter_numerical_number': 1, 'chapter_full_title': 'Chapter 1: Title'},\n    {'chapter_numerical_number': null, 'chapter_full_title': 'Preface'}\n  ]\n}\n"
        "Include all numbered chapters as well as non-numbered sections like 'Preface', 'Introduction', 'Conclusion', etc.\n"
        "If you cannot find a Table of Contents, return 'toc': [] but still try to provide the book title and authors if present.\n"
        f"TEXT FROM FIRST 15 PAGES:\n{extracted_text}"
    )
    import re
    def parse_gemini_json(text):
        cleaned = re.sub(r"^```json|```$", "", text.strip(), flags=re.MULTILINE)
        try:
            return json.loads(cleaned)
        except Exception:
            return {}

    result1 = model.generate_content(prompt1)
    book_info = parse_gemini_json(result1.text)

    # --- Gemini Prompt 2: Match expected chapters to PDF headings ---
    # Prepare variables for prompt
    book_title = book_info.get('book_title') if isinstance(book_info, dict) else ''
    real_chapters = book_info.get('toc') if isinstance(book_info, dict) else []
    rawData = headings
    prompt2 = (
        f"EXPECTED CHAPTERS from Table of Contents for \"{book_title}\":\n"
        f"{json.dumps(real_chapters, indent=2)}\n\n"
        f"PDF HEADINGS FOUND:\n{json.dumps(rawData[:30] if isinstance(rawData, list) else rawData, indent=2)}\n\n"
        "Task: For each expected chapter, find the best matching PDF heading and return the complete list with page numbers.\n"
        "Return ALL expected chapters in this exact JSON format:\n"
        "[\n  {'chapter_numerical_number': 1, 'chapter_full_title': 'Chapter 1: Exact Title', 'page_start': 25},\n  {'chapter_numerical_number': null, 'chapter_full_title': 'Introduction', 'page_start': 5}\n]\n"
        "Rules:\n"
        "- Include ALL chapters from the expected list\n"
        "- Use exact titles from expected chapters\n"
        "- Find page numbers from PDF headings by matching similar titles\n"
        "- If no page found, use 0 as page_start\n"
        "- Return complete JSON array with all chapters"
    )
    result2 = model.generate_content(prompt2)
    matched_chapters = parse_gemini_json(result2.text)

    # Try to parse the first 15 pages text as JSON if possible, else keep as string
    try:
        first_15_json = json.loads(extracted_text)
    except Exception:
        first_15_json = extracted_text

    # Format cleaned_headings.json as requested
    cleaned_path = os.path.join(os.path.dirname(__file__), "..", "cleaned_headings.json")
    cleaned_json = {
        "book_title": book_info.get("book_title") if isinstance(book_info, dict) else "",
        "authors": book_info.get("authors") if isinstance(book_info, dict) else [],
        "toc": []
    }
    # Use matched_chapters if available, else fallback to book_info['toc']
    toc_source = matched_chapters if isinstance(matched_chapters, list) and matched_chapters else (book_info.get("toc") if isinstance(book_info, dict) else [])
    for ch in toc_source:
        cleaned_json["toc"].append({
            "chapter_numerical_number": ch.get("chapter_numerical_number"),
            "chapter_full_title": ch.get("chapter_full_title"),
            "page_number": ch.get("page_start") if "page_start" in ch else ch.get("page_number", 0)
        })

    with open(cleaned_path, "w", encoding="utf-8") as f:
        json.dump(cleaned_json, f, ensure_ascii=False, indent=2)

    return {
        "detected_headings_file": detected_path,
        "cleaned_headings_file": cleaned_path,
        "cleaned_headings": cleaned_json,
        "detected_headings": headings,
        "first_15_pages_text": first_15_json
    }
