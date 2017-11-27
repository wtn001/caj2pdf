package com.oddrock.caj2pdf;

import java.awt.AWTException;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.mail.MessagingException;
import org.apache.log4j.Logger;
import org.jnativehook.GlobalScreen;
import org.jnativehook.NativeHookException;

import com.oddrock.caj2pdf.utils.CajViewerUtils;
import com.oddrock.caj2pdf.utils.Common;
import com.oddrock.caj2pdf.utils.FoxitUtils;
import com.oddrock.caj2pdf.utils.Prop;
import com.oddrock.caj2pdf.utils.TransformRuleUtils;
import com.oddrock.common.DateUtils;
import com.oddrock.common.awt.RobotManager;
import com.oddrock.common.file.FileUtils;
import com.oddrock.common.pdf.PdfManager;
import com.oddrock.common.windows.ClipboardUtils;
import com.oddrock.common.windows.CmdExecutor;
import com.oddrock.common.windows.GlobalKeyListener;

/**
 * 自动化从caj打印pdf
 * @author oddrock
 *
 */
public class Caj2pdfTransformerNew {
	private static Logger logger = Logger.getLogger(Caj2pdfTransformerNew.class);
	private RobotManager robotMngr;
	private List<File> srcFileList;		// 已转换好的源文件
	private List<File> dstFileList;		// 已转换好的目标文件
	private File dstDir;
	public Caj2pdfTransformerNew() throws AWTException, NativeHookException {
		super();
		robotMngr = new RobotManager();
		srcFileList = new ArrayList<File>();
		dstFileList = new ArrayList<File>();
		if(Boolean.parseBoolean(Prop.get("needesckey"))){
			GlobalScreen.registerNativeHook();//初始化ESC钩子 
	        GlobalScreen.addNativeKeyListener(new GlobalKeyListener());
		}
	}
	
	private void generateDstDir() {
		dstDir = new File(Prop.get("dstdirpath")+File.separator+DateUtils.timeStrInChinese());
	}
	
	/**
	 * 打印单个文件
	 * @param srcCajFilePath
	 * @throws IOException
	 * @throws InterruptedException
	 */
	@SuppressWarnings("deprecation")
	public void caj2pdf(String srcCajFilePath) throws IOException, InterruptedException{
		Common.moveMouseAvoidHandicap(robotMngr);
		CajViewerUtils.closeCaj();
		while(CajViewerUtils.isOpen(robotMngr)){
			logger.warn("等待关闭caj");
			Common.wait(Prop.getInt("interval.waitmillis"));
		}
		Common.wait(Prop.getInt("interval.waitmillis"));
		File srcFile = new File(srcCajFilePath);
		if(!srcFile.exists() || !srcFile.isFile() || !srcFile.getName().endsWith(".caj")){
			return;
		}
		CajViewerUtils.openCajOld(srcFile.getCanonicalPath());
		// 检查caj是否完全打开，没有就等待
		while(!CajViewerUtils.isOpen(robotMngr)){
			logger.warn("等待打开caj");
			Common.wait(Prop.getInt("interval.waitmillis"));
		}
		// 打开打印机
		robotMngr.pressCombinationKey(KeyEvent.VK_CONTROL, KeyEvent.VK_P);
		// 检查打印机是否打开，没有就等待
		while(!CajViewerUtils.isPrintReady(robotMngr)){
			logger.warn("等待打开打印机");
			Common.wait(Prop.getInt("interval.waitmillis"));
		}
		robotMngr.pressCombinationKey(KeyEvent.VK_ALT, KeyEvent.VK_O);
		// 检查是否要输入文件名了，没有就等待
		while(!CajViewerUtils.isInputfilename(robotMngr)){
			logger.warn("等待输入文件名");
			Common.wait(Prop.getInt("interval.waitmillis"));
		}
		File dstFile = new File(srcFile.getParent(), srcFile.getName().replaceAll(".caj$", ""));
		// 将生成的pdf文件名复制到文本框
		ClipboardUtils.setSysClipboardText(dstFile.getCanonicalPath());
		Common.wait(Prop.getInt("interval.waitmillis"));
		robotMngr.pressCombinationKey(KeyEvent.VK_CONTROL, KeyEvent.VK_A);
		Common.wait(Prop.getInt("interval.waitmillis"));
		robotMngr.pressCombinationKey(KeyEvent.VK_CONTROL, KeyEvent.VK_V);
		Common.wait(Prop.getInt("interval.waitmillis"));
		// 点击确定按钮
		robotMngr.pressCombinationKey(KeyEvent.VK_ALT, KeyEvent.VK_S);
		boolean printnow = false;
		robotMngr.pressKey(KeyEvent.VK_Y);
		while(true){
			if(CajViewerUtils.isPrintnow(robotMngr)){
				printnow = true;
			}else if(printnow){
				break;
			}
			if(printnow){
				logger.warn("等待打印完毕");
			}else{
				logger.warn("等待开始打印");
			}
			
			Common.wait(Prop.getInt("interval.waitminmillis"));
		}
		Common.wait(Prop.getInt("interval.waitmillis"));
		CajViewerUtils.closeCaj();
		while(CajViewerUtils.isOpen(robotMngr)){
			logger.warn("等待关闭caj");
			Common.wait(Prop.getInt("interval.waitmillis"));
		}
		FoxitUtils.closeFoxit();
		Common.wait(Prop.getInt("interval.waitmillis"));
		logger.warn("完成打印，文件位置："+dstFile.getCanonicalPath());
	}
	
	public void caj2pdfBatch(String srcDirPath) throws IOException, InterruptedException, MessagingException{
		File srcDir = new File(srcDirPath);
		if(!srcDir.exists() || !srcDir.isDirectory()){
			return;
		}
		for(File file : srcDir.listFiles()){
			caj2pdf(file.getCanonicalPath());
		}
		logger.warn("完成"+ srcDir.getCanonicalPath() +"目录下所有caj打印成pdf！");
		// 完成后声音通知
		Common.noticeSound();
		// 完成后短信通知
		Common.noticeMail("所有caj文件转换为PDF已完成！！！");
	}
	
	/*
	 * 第一步，打开caj
	 */
	@SuppressWarnings("deprecation")
	private File caj2pdf_abbyy_step1_opencaj(String srcCajFilePath) throws IOException, InterruptedException {
		// 鼠标挪开，避免挡事
		robotMngr.moveMouseToRightDownCorner(Prop.getInt("xgap"),Prop.getInt("ygap"));
		CajViewerUtils.closeCaj();
		while(CajViewerUtils.isOpen(robotMngr)){
			logger.warn("等待关闭caj");
			Common.wait(Prop.getInt("interval.waitmillis"));
		}
		Common.wait(Prop.getInt("interval.waitmillis"));
		File srcFile = new File(srcCajFilePath);
		if(!srcFile.exists() || !srcFile.isFile() || !srcFile.getName().endsWith(".caj")){
			return null;
		}
		CajViewerUtils.openCajOld(srcFile.getCanonicalPath());
		return srcFile;
	}
	
	// 第二步，打开打印机
	private void caj2pdf_abbyy_step2_openprinter() throws IOException, InterruptedException {
		// 检查caj是否完全打开，没有就等待
		while(!CajViewerUtils.isOpen(robotMngr)){
			logger.warn("等待打开caj");
			Common.wait(Prop.getInt("interval.waitmillis"));
		}
		// 打开打印机
		robotMngr.pressCombinationKey(KeyEvent.VK_CONTROL, KeyEvent.VK_P);
	}
	
	// 第三步，开始打印
	private void caj2pdf_abbyy_step3_startprint() throws IOException, InterruptedException {
		// 检查打印机是否打开，没有就等待
		while(!CajViewerUtils.isPrintReady(robotMngr)){
			logger.warn("等待打开打印机");
			Common.wait(Prop.getInt("interval.waitmillis"));
		}
		robotMngr.pressCombinationKey(KeyEvent.VK_ALT, KeyEvent.VK_O);
	}
	
	/*
	 * 第四步，等待完成打印
	 */
	private void caj2pdf_abbyy_step4_waitprintfinished() throws IOException, InterruptedException {
		boolean hasprinted = false;
		while(true){
			if(CajViewerUtils.isPrintnow(robotMngr)){
				hasprinted = true;
			}else if(hasprinted){
				break;
			}
			if(hasprinted){
				logger.warn("等待打印完毕");
			}else{
				logger.warn("等待开始打印");
			}
			Common.wait(Prop.getInt("interval.waitminmillis"));
		}
		Common.wait(Prop.getInt("interval.waitmillis"));
	}
	
	private File caj2pdf_abbyy_step5_inputfilename(File srcFile) throws IOException, InterruptedException {
		// 检查是否要输入文件名了，没有就等待
		while(!CajViewerUtils.isInputfilename(robotMngr)){
			logger.warn("等待输入文件名");
			Common.wait(Prop.getInt("interval.waitmillis"));
		}
		File dstFile = new File(srcFile.getParent(), srcFile.getName().replaceAll(".caj$", ".pdf"));
		// 将生成的pdf文件名复制到文本框
		ClipboardUtils.setSysClipboardText(dstFile.getCanonicalPath());
		Common.wait(Prop.getInt("interval.waitmillis"));
		robotMngr.pressCombinationKey(KeyEvent.VK_CONTROL, KeyEvent.VK_A);
		Common.wait(Prop.getInt("interval.waitmillis"));
		robotMngr.pressCombinationKey(KeyEvent.VK_CONTROL, KeyEvent.VK_V);
		Common.wait(Prop.getInt("interval.waitmillis"));
		// 点击确定按钮
		robotMngr.pressCombinationKey(KeyEvent.VK_ALT, KeyEvent.VK_S);
		// 点击确定覆盖按钮
		robotMngr.pressKey(KeyEvent.VK_ENTER);
		Common.wait(Prop.getInt("interval.waitlongmillis"));
		return dstFile;
	}
	
	/*
	 * 结束
	 */
	private void caj2pdf_abbyy_step6_end(File dstFile) throws IOException, InterruptedException {
		CajViewerUtils.closeCaj();
		while(CajViewerUtils.isOpen(robotMngr)){
			logger.warn("等待关闭caj");
			Common.wait(Prop.getInt("interval.waitmillis"));
		}
		Common.wait(Prop.getInt("interval.waitmillis"));
		logger.warn("完成打印，文件位置："+dstFile.getCanonicalPath());
	}
	
	/**
	 * 将所有文件从源文件夹移动到目标文件夹
	 * @return
	 * @throws IOException
	 */
	private void mvAllFilesFromSrcToDst() throws IOException {
		if(dstFileList.size()==0) {
			return;
		}
		if(!Prop.getBool("needmovesrc2dst")){
			dstDir = new File(Prop.get("srcdirpath"));
			return;
		}
		generateDstDir();
		if(!dstDir.exists() || !dstDir.isDirectory()) {
			dstDir.mkdirs();
		}
		for(File file: srcFileList) {
			FileUtils.moveFile(file.getCanonicalPath(), dstDir.getCanonicalPath());
		}
		for(File file: dstFileList) {
			FileUtils.moveFile(file.getCanonicalPath(), dstDir.getCanonicalPath());
		}
	}
	
	// 打开已完成文件所在目录的窗口
	private void openFinishedWindows() throws IOException {
		if(!Prop.getBool("needopenfinishedwindows")){
			return;
		}
		CmdExecutor.getSingleInstance().openDirWindows(dstDir.getCanonicalPath());	
	}
	
	/**
	 * 第二种caj2pdf方式，适用于ABBYY
	 * @param srcCajFilePath
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public File caj2pdf_abbyy(String srcCajFilePath) throws IOException, InterruptedException{
		File srcFile = caj2pdf_abbyy_step1_opencaj(srcCajFilePath);
		if(srcFile==null) {
			return null;
		}
		caj2pdf_abbyy_step2_openprinter();
		caj2pdf_abbyy_step3_startprint();
		caj2pdf_abbyy_step4_waitprintfinished();
		File dstFile = caj2pdf_abbyy_step5_inputfilename(srcFile);
		caj2pdf_abbyy_step6_end(dstFile);
		srcFileList.add(srcFile);
		dstFileList.add(dstFile);
		return dstFile;
	}
	
	/**
	 * 第二种caj2pdf方式，适用于ABBYY
	 * @param srcDirPath
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws MessagingException
	 */
	@SuppressWarnings("deprecation")
	public void caj2pdfBatch_abbyy(String srcDirPath) throws IOException, InterruptedException, MessagingException{
		File srcDir = new File(srcDirPath);
		if(!srcDir.exists() || !srcDir.isDirectory()){
			return;
		}
		for(File file : srcDir.listFiles()){
			caj2pdf_abbyy(file.getCanonicalPath());
		}
		logger.warn("完成"+ srcDir.getCanonicalPath() +"目录下所有caj打印成pdf！");
		// 完成后声音通知
		Common.noticeSound();
		// 完成后短信通知
		Common.noticeMail("所有caj文件转换为PDF已完成！！！");
		FoxitUtils.closeFoxit();
		Common.wait(Prop.getInt("interval.waitmillis"));
		// 将所有文件转移到目标文件夹
		mvAllFilesFromSrcToDst();
		// 打开完成窗口
		openFinishedWindows();
	}

	
	/**
	 * 试转页面
	 * @param srcDirPath
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws MessagingException
	 */
	@SuppressWarnings("deprecation")
	public void caj2pdftest_abbyy(String srcDirPath) throws IOException, InterruptedException, MessagingException {
		// 鼠标挪开，避免挡事
		robotMngr.moveMouseToRightDownCorner(Prop.getInt("xgap"),Prop.getInt("ygap"));
		File srcDir = new File(srcDirPath);
		if(!srcDir.exists() || !srcDir.isDirectory() || srcDir.listFiles().length==0){
			return;
		}
		File srcFile = null;
		for(File file : srcDir.listFiles()) {
			if(file.getName().endsWith(".caj")) {
				srcFile = file;
				break;
			}
		}
		if(srcFile==null) {
			return;
		}
		File dstFile = caj2pdf_abbyy(srcFile.getCanonicalPath());
		FoxitUtils.closeFoxit();
		Common.wait(Prop.getInt("interval.waitminmillis"));
		while(FoxitUtils.isOpen(robotMngr)) {
			Common.wait(Prop.getInt("interval.waitmillis"));
			logger.warn("等待pdf关闭");
		}
		int pageCount = new PdfManager().pdfPageCount(dstFile.getCanonicalPath());
		if(pageCount!=1) {
			int testcount = TransformRuleUtils.computeTestPageCount(pageCount);
			FoxitUtils.openPdfOld(dstFile.getCanonicalPath());
			Common.wait(Prop.getInt("interval.waitminmillis"));
			while(!FoxitUtils.isOpen(robotMngr)) {
				Common.wait(Prop.getInt("interval.waitmillis"));
				logger.warn("等待pdf打开");
			}
			// 打开页面管理菜单
			robotMngr.pressCombinationKey(KeyEvent.VK_ALT, KeyEvent.VK_O);
			Common.wait(Prop.getInt("interval.waitminmillis"));
			// 打开提取页面菜单
			robotMngr.pressKey(KeyEvent.VK_E);
			Common.wait(Prop.getInt("interval.waitminmillis"));
			while(!FoxitUtils.isExportPageOpenAtExtractPage(robotMngr)) {
				logger.warn("等待打开提取页面的导出页面");
				Common.wait(Prop.getInt("interval.waitminmillis"));
			}
			// 两次tab移动到输入数字文本框
			robotMngr.pressKey(KeyEvent.VK_TAB);
			Common.wait(Prop.getInt("interval.waitminmillis"));
			robotMngr.pressKey(KeyEvent.VK_TAB);
			Common.wait(Prop.getInt("interval.waitminmillis"));
			// 全选输入数字文本框
			robotMngr.pressCombinationKey(KeyEvent.VK_CONTROL, KeyEvent.VK_A);
			// 将数字写入粘贴板
			ClipboardUtils.setSysClipboardText(String.valueOf(testcount));
			Common.wait(Prop.getInt("interval.waitminmillis"));
			// 将数字复制到输入数字文本框
			robotMngr.pressCombinationKey(KeyEvent.VK_CONTROL, KeyEvent.VK_V);
			Common.wait(Prop.getInt("interval.waitminmillis"));
			// 选中导出页面另存为其他文档
			robotMngr.pressCombinationKey(KeyEvent.VK_ALT, KeyEvent.VK_S);
			Common.wait(Prop.getInt("interval.waitminmillis"));
			// 点击确认按钮
			robotMngr.pressCombinationKey(KeyEvent.VK_ALT, KeyEvent.VK_K);
			Common.wait(Prop.getInt("interval.waitminmillis"));
			while(!FoxitUtils.isInputfilenameAtExtractPage(robotMngr)) {
				logger.warn("等待打开输入文件名页面");
				Common.wait(Prop.getInt("interval.waitminmillis"));
			}
			dstFile = new File(dstFile.getParentFile(), "提取页面 "+dstFile.getName());
			ClipboardUtils.setSysClipboardText(dstFile.getCanonicalPath());
			Common.wait(Prop.getInt("interval.waitminmillis"));
			robotMngr.pressCombinationKey(KeyEvent.VK_CONTROL, KeyEvent.VK_V);
			Common.wait(Prop.getInt("interval.waitminmillis"));
			// 点击确定按钮
			robotMngr.pressCombinationKey(KeyEvent.VK_ALT, KeyEvent.VK_S);
			Common.wait(Prop.getInt("interval.waitminmillis"));
			// 确认覆盖（如果要覆盖的话）
			robotMngr.pressCombinationKey(KeyEvent.VK_ALT, KeyEvent.VK_Y);
			while(CajViewerUtils.isInputfilename(robotMngr)) {
				logger.warn("等待关闭输入文件名页面");
				Common.wait(Prop.getInt("interval.waitmillis"));
			}
			FoxitUtils.closeFoxit();
			Common.wait(Prop.getInt("interval.waitminmillis"));
			while(FoxitUtils.isOpen(robotMngr)) {
				Common.wait(Prop.getInt("interval.waitmillis"));
				logger.warn("等待pdf关闭");
			}
		}
		if(Prop.getBool("needmovesrc2dst")){	
			generateDstDir();
			FileUtils.moveFile(dstFile.getCanonicalPath(), dstDir.getCanonicalPath());
		}else {
			dstDir = dstFile.getParentFile();
		}
		// 完成后声音通知
		Common.noticeSound();
		// 完成后短信通知
		Common.noticeMail("试转已经完成啦！！！");
		openFinishedWindows();
		Common.wait(Prop.getInt("interval.waitmillis"));
	}
	
	
	public static void main(String[] args) throws AWTException, NativeHookException, IOException, InterruptedException, MessagingException {		
		/*if(1!=0) {
			Caj2pdfTransformerNew cts = new Caj2pdfTransformerNew();
			Thread.sleep(5000);
			System.out.println(JudgerByCapturePics.isABBYYOpen(cts.robotMngr));
			System.out.println(JudgerByCapturePics.isABBYYTasking(cts.robotMngr));
			System.exit(0);
		}*/
		
		String method = Prop.get("caj2pdf.start");
		if(args.length>=1) {
			method = args[0].trim(); 
		}
		if(method==null) {
			method = "start";
		}
		Caj2pdfTransformerNew cts = new Caj2pdfTransformerNew();
		// 第一个启动参数为start，表示做caj2pdf的转换。
		if("start".equalsIgnoreCase(method)) {
			String srcDirPath = Prop.get("srcdirpath");
			cts.caj2pdfBatch(srcDirPath);	
		// 第一个启动参数为start_abbyy，表示做基于abbyy的caj2pdf转换
		}else if("start_abbyy".equalsIgnoreCase(method)) {
			logger.warn("开始abbyy方式打印");
			String srcDirPath = Prop.get("srcdirpath");
			cts.caj2pdfBatch_abbyy(srcDirPath);	
		// 第一个启动参数为start_abbyy_testpdf，表示做基于abbyy的caj2pdf的一个文件的试转换，并提取前20页
		}else if("start_abbyy_test".equalsIgnoreCase(method)) {
			logger.warn("开始abbyy方式试转换");
			String srcDirPath = Prop.get("srcdirpath");
			cts.caj2pdftest_abbyy(srcDirPath);	
		// 第一个启动参数为captureimage，表示进行截图
		}else if("captureimage".equalsIgnoreCase(method)) {
			if(args.length>=5) {
				Thread.sleep(Prop.getInt("interval.waitlongmillis"));
				Common.captureImageAndSave(cts.robotMngr, Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]), Integer.parseInt(args[4]));
			}
		}
	}
}
