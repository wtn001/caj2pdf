package com.oddrock.caj2pdf.main;

import java.awt.AWTException;
import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import javax.mail.MessagingException;
import org.apache.log4j.Logger;
import com.oddrock.caj2pdf.bean.TransformFileSet;
import com.oddrock.caj2pdf.biz.Caj2PdfUtils;
import com.oddrock.caj2pdf.biz.Pdf2WordUtils;
import com.oddrock.caj2pdf.biz.PdfUtils;
import com.oddrock.caj2pdf.utils.CommonUtils;
import com.oddrock.caj2pdf.utils.Prop;
import com.oddrock.caj2pdf.utils.TransformRuleUtils;
import com.oddrock.common.awt.RobotManager;
import com.oddrock.common.pdf.PdfManager;

public class DocFormatConverter {
	private static Logger logger = Logger.getLogger(DocFormatConverter.class);
	private RobotManager robotMngr;
	public DocFormatConverter() throws AWTException {
		super();
		robotMngr = new RobotManager();
	}
	
	/**
	 * 批量caj转换pdf
	 * @throws InterruptedException 
	 * @throws IOException 
	 * @throws MessagingException 
	 */
	public void caj2pdf(File srcDir, File dstDir) throws IOException, InterruptedException, MessagingException {
		if(!srcDir.exists() || !srcDir.isDirectory()){
			return;
		}
		TransformFileSet fileSet;
		Set<File> needMoveFilesSet = new HashSet<File>();
		for(File file : srcDir.listFiles()){
			if(file==null) continue;
			fileSet = Caj2PdfUtils.caj2pdf(robotMngr, file.getCanonicalPath());
			needMoveFilesSet.add(fileSet.getDstFile());
			needMoveFilesSet.add(fileSet.getSrcFile());
		}
		doAfterTransform(srcDir, dstDir, needMoveFilesSet, "caj转pdf已完成");
	}
	
	/**
	 * 批量caj转pdf，用默认的源文件夹和目标文件夹
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws MessagingException
	 */
	public void caj2pdf() throws IOException, InterruptedException, MessagingException {
		caj2pdf(new File(Prop.get("srcdirpath")), new File(Prop.get("dstdirpath")));
	}
	
	// 转换后的动作
	private void doAfterTransform(File srcDir, File dstDir, Set<File> needMoveFilesSet, String noticeContent) throws IOException, MessagingException {
		// 将需要移动的文件移动到目标文件夹
		dstDir = CommonUtils.mvAllFilesFromSrcToDst(needMoveFilesSet, dstDir);
		// 完成后声音通知
		CommonUtils.noticeSound();
		// 完成后短信通知
		CommonUtils.noticeMail(noticeContent);
		// 打开完成后的文件夹窗口
		CommonUtils.openFinishedWindows(dstDir);
		logger.warn(noticeContent+ ":" + srcDir.getCanonicalPath());
	}
	
	/**
	 * 批量caj转word
	 * @throws InterruptedException 
	 * @throws IOException 
	 * @throws MessagingException 
	 */
	public void caj2word(File srcDir, File dstDir) throws IOException, InterruptedException, MessagingException {
		if(!srcDir.exists() || !srcDir.isDirectory()){
			return;
		}
		TransformFileSet fileSet;
		// 存放待转换的pdf文件
		Set<File> pdfFileSet = new HashSet<File>();
		Set<File> needMoveFilesSet = new HashSet<File>();
		// 先全部caj转pdf
		for(File file : srcDir.listFiles()){
			if(file==null) continue;
			fileSet = Caj2PdfUtils.caj2pdf(robotMngr, file.getCanonicalPath());
			needMoveFilesSet.add(fileSet.getDstFile());
			needMoveFilesSet.add(fileSet.getSrcFile());
			pdfFileSet.add(fileSet.getDstFile());
		}
		// 再全部pdf转word
		for(File file : pdfFileSet){
			if(file==null) continue;
			fileSet = Pdf2WordUtils.pdf2word(robotMngr, file.getCanonicalPath());
			needMoveFilesSet.add(fileSet.getDstFile());
			needMoveFilesSet.add(fileSet.getSrcFile());
		}
		doAfterTransform(srcDir, dstDir, needMoveFilesSet, "caj转word已完成");
	}
	
	// caj试转pdf
	public void caj2pdf_test(File srcDir, File dstDir) throws IOException, InterruptedException, MessagingException {
		if(!srcDir.exists() || !srcDir.isDirectory()) return;
		TransformFileSet fileSet = null;
		Set<File> needMoveFilesSet = new HashSet<File>();
		for(File file : srcDir.listFiles()){
			if(file==null) continue;
			// 找到目录下第一个caj，并转换为pdf
			if(file.exists() && file.isFile() && file.getCanonicalPath().endsWith(".caj")) {
				fileSet = Caj2PdfUtils.caj2pdf(robotMngr, file.getCanonicalPath());
				break;
			}
		}
		if(fileSet==null) return;
		// 获得转换得到的pdf的实际页数
		int realPageCount = new PdfManager().pdfPageCount(fileSet.getDstFile().getCanonicalPath());
		// 计算出应该提取的页数
		int tiquPageCount = TransformRuleUtils.computeTestPageCount(realPageCount);
		// 从已转换的pdf中提取相应页数，另存为新的pdf，新的pdf名为在已有PDF名称前加上“提取页面 ”
		fileSet = PdfUtils.extractPage(robotMngr, fileSet.getDstFile().getCanonicalPath(), tiquPageCount);
		// 将需要移动的文档记录下来
		needMoveFilesSet.add(fileSet.getDstFile());
		// 进行完成后的各项通知和扫尾工作
		doAfterTransform(srcDir, dstDir, needMoveFilesSet, "caj试转pdf已完成");
	}
	
	// caj试转pdf
	public void caj2pdf_test() throws IOException, InterruptedException, MessagingException {
		caj2pdf_test(new File(Prop.get("srcdirpath")), new File(Prop.get("dstdirpath")));
	}
	
	// caj试转word
	public void caj2word_test(File srcDir, File dstDir) throws IOException, InterruptedException, MessagingException {
		if(!srcDir.exists() || !srcDir.isDirectory()) return;
		TransformFileSet fileSet = null;
		Set<File> needMoveFilesSet = new HashSet<File>();
		for(File file : srcDir.listFiles()){
			if(file==null) continue;
			// 找到目录下第一个caj，并转换为pdf
			if(file.exists() && file.isFile() && file.getCanonicalPath().endsWith(".caj")) {
				fileSet = Caj2PdfUtils.caj2pdf(robotMngr, file.getCanonicalPath());
				break;
			}
		}
		if(fileSet==null) return;
		// 获得转换得到的pdf的实际页数
		int realPageCount = new PdfManager().pdfPageCount(fileSet.getDstFile().getCanonicalPath());
		// 计算出应该提取的页数
		int tiquPageCount = TransformRuleUtils.computeTestPageCount(realPageCount);
		// 从已转换的pdf中提取相应页数，另存为新的pdf，新的pdf名为在已有PDF名称前加上“提取页面 ”
		fileSet = PdfUtils.extractPage(robotMngr, fileSet.getDstFile().getCanonicalPath(), tiquPageCount);
		// 将提取后的页面转为word
		fileSet = Pdf2WordUtils.pdf2word(robotMngr, fileSet.getDstFile().getCanonicalPath());
		// 将转换后的文档移动到目标目录，如果需要的话
		needMoveFilesSet.add(fileSet.getSrcFile());
		needMoveFilesSet.add(fileSet.getDstFile());
		// 进行完成后的各项通知和扫尾工作
		doAfterTransform(srcDir, dstDir, needMoveFilesSet, "caj试转word已完成");
	}
	
	public void caj2word_test() throws IOException, InterruptedException, MessagingException{
		caj2word_test(new File(Prop.get("srcdirpath")), new File(Prop.get("dstdirpath")));
	}
	
	public void caj2word() throws IOException, InterruptedException, MessagingException {
		caj2word(new File(Prop.get("srcdirpath")), new File(Prop.get("dstdirpath")));
	}
	
	public static void main(String[] args) throws AWTException, IOException, InterruptedException, MessagingException {
		DocFormatConverter dfc = new DocFormatConverter();
		dfc.caj2word_test();
	}
}
