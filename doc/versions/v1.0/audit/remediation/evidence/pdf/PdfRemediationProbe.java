import com.ainovel.app.v2.export.*;
import com.ainovel.app.common.BusinessException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.rendering.PDFRenderer;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.io.*;
import javax.imageio.ImageIO;
public class PdfRemediationProbe {
 public static void main(String[] args) throws Exception {
  Path root=Path.of(args[0]); Files.createDirectories(root);
  String para="林砚收到一封信。English ABC 123，标点：《故事》！\uD840\uDC87";
  String text="中文小说：导出完整性验收\n第一章 邮戳\n"+(para.repeat(3)+"\n").repeat(64)+"终章：全文结束。";
  var out=new ByteArrayOutputStream();
  new PdfExportRenderer().render(new ExportDocument("中文小说","审计样稿",text,StandardCharsets.UTF_8),out);
  Files.write(root.resolve("chinese-pagination.pdf"),out.toByteArray());
  try(var pdf=Loader.loadPDF(out.toByteArray())) {
   String extracted=new PDFTextStripper().getText(pdf);
   if(!text.replaceAll("\\s+","").equals(extracted.replaceAll("\\s+",""))) throw new AssertionError("PDF text differs");
   if(pdf.getNumberOfPages()<3) throw new AssertionError("Pagination absent");
   for(var page:pdf.getPages()) for(var n:page.getResources().getFontNames()) if(!page.getResources().getFont(n).isEmbedded()) throw new AssertionError("Font not embedded");
   var renderer=new PDFRenderer(pdf);
   for(int page:new int[]{0,pdf.getNumberOfPages()/2,pdf.getNumberOfPages()-1}) ImageIO.write(renderer.renderImageWithDPI(page,110),"PNG",root.resolve("page-"+(page+1)+".png").toFile());
   System.out.println("pages="+pdf.getNumberOfPages()+"; unicodeRoundTrip=PASS; embeddedFonts=PASS; firstMiddleLastRendered=PASS");
  }
  var rejected=new ByteArrayOutputStream();
  try {new PdfExportRenderer().render(new ExportDocument("test","author","\uD83D\uDE00",StandardCharsets.UTF_8),rejected);throw new AssertionError("Missing glyph accepted");}
  catch(BusinessException expected){if(!expected.getMessage().equals("PDF_UNSUPPORTED_CHARACTER_U+1F600")) throw expected;}
  if(rejected.size()!=0) throw new AssertionError("Failed PDF leaked bytes");
  System.out.println("unsupportedGlyph=FAIL_CLOSED; failedOutputBytes=0");
 }
}
