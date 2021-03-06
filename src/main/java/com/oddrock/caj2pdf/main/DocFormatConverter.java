package com.oddrock.caj2pdf.main;

import java.awt.AWTException;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import javax.mail.MessagingException;
import org.apache.log4j.Logger;
import org.codehaus.plexus.util.ExceptionUtils;

import com.oddrock.caj2pdf.bean.TransformFileSetEx;
import com.oddrock.caj2pdf.biz.Caj2PdfUtils;
import com.oddrock.caj2pdf.biz.Caj2WordUtils;
import com.oddrock.caj2pdf.biz.Html2PdfUtils;
import com.oddrock.caj2pdf.biz.Img2WordUtils;
import com.oddrock.caj2pdf.biz.Pdf2EpubUtils;
import com.oddrock.caj2pdf.biz.Pdf2MobiUtils;
import com.oddrock.caj2pdf.biz.Pdf2WordUtils;
import com.oddrock.caj2pdf.biz.Txt2MobiUtils;
import com.oddrock.caj2pdf.constant.MailFileType;
import com.oddrock.caj2pdf.constant.TransformType;
import com.oddrock.caj2pdf.exception.TransformNodirException;
import com.oddrock.caj2pdf.exception.TransformNofileException;
import com.oddrock.caj2pdf.exception.TransformPdfEncryptException;
import com.oddrock.caj2pdf.exception.TransformWaitTimeoutException;
import com.oddrock.caj2pdf.persist.DocBakUtils;
import com.oddrock.caj2pdf.persist.TransformInfoStater;
import com.oddrock.caj2pdf.qqmail.MailDir;
import com.oddrock.caj2pdf.qqmail.QQMailArchiveUtils;
import com.oddrock.caj2pdf.qqmail.QQMailRcvUtils;
import com.oddrock.caj2pdf.qqmail.QQMailSendUtils;
import com.oddrock.caj2pdf.selftest.SelftestFilesPool;
import com.oddrock.caj2pdf.selftest.SelftestRuleUtils;
import com.oddrock.caj2pdf.selftest.bean.SelftestRule;
import com.oddrock.caj2pdf.utils.Common;
import com.oddrock.caj2pdf.utils.DateStrTransformDstDirGenerator;
import com.oddrock.caj2pdf.utils.MailDateStrTransformDstDirGenerator;
import com.oddrock.caj2pdf.utils.AsnycHiddenFileDeleter;
import com.oddrock.caj2pdf.utils.AsyncDbSaver;
import com.oddrock.caj2pdf.utils.Prop;
import com.oddrock.common.awt.RobotManager;
import com.oddrock.common.file.FileUtils;
import com.oddrock.common.windows.ClipboardUtils;
import com.oddrock.common.windows.CmdExecutor;
import com.oddrock.common.zip.UnZipUtils;

public class DocFormatConverter {
	public static boolean selftest = false;
	private static Logger logger = Logger.getLogger(DocFormatConverter.class);
	private RobotManager robotMngr;
	public DocFormatConverter() throws AWTException {
		super();
		robotMngr = new RobotManager();
	}
	
	private void doBeforeTransform(TransformInfoStater tfis) throws TransformNodirException {
		File srcDir = tfis.getSrcDir();
		if(!srcDir.exists() || !srcDir.isDirectory()){
			throw new TransformNodirException(srcDir+"：该目录不存在！");
		}
		if(Prop.getBool("deletehiddenfile")) {
			// 删除隐藏文件
			FileUtils.deleteHiddenFiles(srcDir);
		}
		for(File file : srcDir.listFiles()) {
			if(file.isHidden() || file.isDirectory()) continue;
			String fileName = file.getName();
			// 看文件名中是否有多个连续的空格，如果有，则替换为1个空格。
			// 因为名字里有两个空格的文件，无法用CmdExecutor打开
			if(fileName.matches(".*\\s{2,}.*")) {
				fileName = fileName.replaceAll("\\s{2,}", " ");
				file.renameTo(new File(srcDir, fileName));
			}
			// 有的caj文件用nh结尾，需要修改后缀名
			if(fileName.matches("^.*\\.nh$")) {
				fileName = fileName.replaceAll("\\.nh$", ".caj");
				file.renameTo(new File(srcDir, fileName));
			}
		}
	}
	
	// 转换后的动作
	private void doAfterTransform(TransformInfoStater tfis) throws IOException, MessagingException {
		String noticeContent = tfis.getInfo().getTransform_type().replace("2", "转") + "已完成";
		boolean debug = Prop.getBool("debug");
		boolean simureal = Prop.getBool("simureal");
		/*boolean isError = false;
		TransformException exception = null;*/
		// 如果需要发邮件
		if(tfis.isNeedSendDstFileMail()) {
			try {
				if(selftest || debug) {	// 如果是自测，始终只发给自测的邮箱，避免骚扰用户
					tfis.getMaildir().setFromEmail(Prop.get("selftest.mail.recver.accounts"));
				}
				QQMailSendUtils.sendMailWithFile(tfis);
			}catch(Exception e) {
				e.printStackTrace();
				/*isError = true;
				exception = new TransformSendFileMailException("发送邮件发生异常");*/
				noticeContent += "但发送邮件失败，请手动发送邮件！";
			}
		}
		// 如果在调试或者自测,且不需要仿真，就不需要备份
		if(((!debug && !selftest) || simureal) && Prop.getBool("docbak.need")) {
			// 备份不是必须步骤，任何异常不要影响正常流程
			try {
				// 备份文件，以便未来测试
				DocBakUtils.bakDoc(tfis.getInfo().getTransform_type(), tfis.getSrcFileSet());
			}catch(Exception e) {
				e.printStackTrace();
			}
		}
		if(tfis.isNeedDelMidFile()){
			for(File file: tfis.getMidFileSet()) {
				file.delete();
			}
		}
		// 不是自测或者是在仿真，才需要复制文件
		if(!selftest || simureal) {
			// 将需要移动的文件移动到目标文件夹
			if(tfis.isNeedMoveSrcFile() || tfis.isNeedMoveMidFile() || tfis.isNeedMoveDstFile()) {
				if(tfis.isNeedMoveSrcFile() && 
							(!tfis.getInfo().getTransform_type().contains("test") 
							|| tfis.isTesttransformNeedMoveSrcFile())) {
					Common.mvFileSet(tfis.getSrcFileSet(), tfis.getDstDir());	
				}
				if(tfis.isNeedMoveMidFile() && !tfis.getInfo().getTransform_type().contains("test")) {
					Common.mvFileSet(tfis.getMidFileSet(), tfis.getDstDir());
				}
				if(tfis.isNeedMoveDstFile()) {
					Common.mvFileSet(tfis.getDstFileSet(), tfis.getDstDir());
				}
			}
		}
		
		
		// 如果在调试或者自测,且不需要仿真，就不需要通知
		if((!debug && !selftest) || simureal) {
			// 通知不是必须步骤，任何异常不要影响正常流程
			try {
				// 完成后声音通知
				Common.noticeSound();
				if(tfis.isNeedNoticeMail()) {
					// 完成后短信通知
					Common.noticeMail(noticeContent);
				}
			}catch(Exception e) {
				e.printStackTrace();
			}
		}
		// 如果在自测,且不需要仿真，就不需要打开文件窗口
		if((!selftest || simureal) && Prop.getBool("needopenfinishedwindows") && tfis.isNeedOpenFinisedWindows()) {
			// 打开完成后的文件夹窗口
			if(Prop.getBool("needmove.dstfile")) {		
				Common.openFinishedWindows(tfis.getDstDir());
				//System.out.println(tfis.getDstDir().getCanonicalPath());
				FileUtils.copyFileInSrcDirToDstDir(new File(Prop.get("bat.openfinishedwindows.dirpath")), tfis.getDstDir());
			}else {
				Common.openFinishedWindows(tfis.getSrcDir());
				FileUtils.copyFileInSrcDirToDstDir(new File(Prop.get("bat.openfinishedwindows.dirpath")), tfis.getSrcDir());
			}	
		}
		// 如果在调试或者自测,且不需要仿真，就不需要修改桌面快捷方式
		if(((!debug && !selftest) || simureal) && Prop.getBool("bat.directtofinishedwindows.need")) {
			// 在桌面生成一个已完成文件夹的bat文件，可以一运行立刻打开文件夹
			Common.createBatDirectToFinishedWindows(tfis.getDstDir());
		}
		if(Prop.getBool("deletehiddenfile")) {
			// 删除隐藏文件
			AsnycHiddenFileDeleter.delete(tfis.getSrcDir());
		}
		// 如果要删除源文件夹，就删除源文件夹
		if(tfis.isNeedDelSrcDir()) {
			FileUtils.deleteDirAndAllFiles(tfis.getSrcDir());
		}
		if(debug) {
			tfis.getInfo().setSelftest(1); 		// debug也算测试，避免误导数据
		}
		if(tfis.isNeedSaveDb()) {
			// 保存信息到数据库
			AsyncDbSaver.saveDb(tfis);
		}
		if(tfis.isNeedCopyContentOnClipboard()) {
			ClipboardUtils.setSysClipboardText(tfis.getClipboardContent());
		}
		logger.warn(noticeContent+ ":" + tfis.getSrcDir().getCanonicalPath());
		
	}
	
	private void doAfter(String noticeContent, File dstDir, boolean exception) throws IOException {
		boolean debug = Prop.getBool("debug");
		boolean needopenfinishedwindows = Prop.getBool("needopenfinishedwindows");
		boolean simureal = Prop.getBool("simureal");
		// 如果在调试或者自测,且不需要仿真，就不需要通知
		if((!debug && !selftest) || simureal) {
			// 通知不是必须步骤，任何异常不要影响正常流程
			try {
				if(!exception) {
					// 完成后声音通知
					Common.noticeSound();
					// 完成后不再短信通知
					//Common.noticeMail(noticeContent);
				}else {
					// 声音告警
					Common.noticeAlertSound();
					// 邮件告警
					Common.noticeAlertMail(noticeContent);
				}
			}catch(Exception e) {
				e.printStackTrace();
			}
		}
		// 如果在自测,且不需要仿真，就不需要打开文件窗口
		if((!selftest || simureal) && needopenfinishedwindows) {
			if(dstDir!=null && dstDir.exists()) {
				Common.openFinishedWindows(dstDir);
			}
		}
		
	}
	
	// 批量caj转pdf
	public void caj2pdf(File srcDir, File dstDir) throws IOException, InterruptedException, MessagingException, TransformWaitTimeoutException, TransformNofileException, TransformNodirException {
		TransformInfoStater tfis = new TransformInfoStater("caj2pdf", srcDir, dstDir, robotMngr);
		doBeforeTransform(tfis);	
		Caj2PdfUtils.caj2pdf_batch(tfis);
		doAfterTransform(tfis);
		
	}
	
	// 批量caj转pdf，用默认的源文件夹和目标文件夹
	public void caj2pdf() throws IOException, InterruptedException, MessagingException, TransformWaitTimeoutException, TransformNofileException, TransformNodirException {
		caj2pdf(new File(Prop.get("srcdirpath")), new File(Prop.get("dstdirpath")));
	}
	
	// 批量caj转word
	public void caj2word(File srcDir, File dstDir) throws IOException, InterruptedException, MessagingException, TransformWaitTimeoutException, TransformNofileException, TransformNodirException {
		TransformInfoStater tfis = new TransformInfoStater("caj2word", srcDir, dstDir, robotMngr);
		doBeforeTransform(tfis);
		Caj2WordUtils.caj2word_batch(tfis);
		doAfterTransform(tfis);
	}
	
	// 批量caj转word
	public void caj2word() throws IOException, InterruptedException, MessagingException, TransformWaitTimeoutException, TransformNofileException, TransformNodirException {
		caj2word(new File(Prop.get("srcdirpath")), new File(Prop.get("dstdirpath")));
	}
	
	// caj试转pdf
	public void caj2pdf_test(File srcDir, File dstDir) throws IOException, InterruptedException, MessagingException, TransformWaitTimeoutException, TransformNofileException, TransformNodirException, TransformPdfEncryptException {
		TransformInfoStater tfis = new TransformInfoStater("caj2pdf_test",srcDir, dstDir, robotMngr);
		doBeforeTransform(tfis);
		Caj2PdfUtils.caj2pdf_test(tfis);
		doAfterTransform(tfis);
	}
	
	// caj试转pdf
	public void caj2pdf_test() throws IOException, InterruptedException, MessagingException, TransformWaitTimeoutException, TransformNofileException, TransformNodirException, TransformPdfEncryptException {
		caj2pdf_test(new File(Prop.get("srcdirpath")), new File(Prop.get("dstdirpath")));
	}
	
	// caj试转word
	public void caj2word_test(File srcDir, File dstDir) throws IOException, InterruptedException, MessagingException, TransformWaitTimeoutException, TransformNofileException, TransformNodirException, TransformPdfEncryptException {
		TransformInfoStater tfis = new TransformInfoStater("caj2word_test",srcDir, dstDir, robotMngr);
		doBeforeTransform(tfis);
		Caj2WordUtils.caj2word_test(tfis);	
		doAfterTransform(tfis);
	}
	
	// caj试转word
	public void caj2word_test() throws IOException, InterruptedException, MessagingException, TransformWaitTimeoutException, TransformNofileException, TransformNodirException, TransformPdfEncryptException{
		caj2word_test(new File(Prop.get("srcdirpath")), new File(Prop.get("dstdirpath")));
	}
	
	// pdf批量转word
	public void pdf2word(File srcDir, File dstDir) throws IOException, InterruptedException, MessagingException, TransformNofileException, TransformNodirException, TransformWaitTimeoutException {
		TransformInfoStater tfis = new TransformInfoStater("pdf2word",srcDir, dstDir, robotMngr);
		doBeforeTransform(tfis);
		Pdf2WordUtils.pdf2word_batch(tfis);
		doAfterTransform(tfis);
	}
	
	// pdf批量转word
	public void pdf2word() throws IOException, InterruptedException, MessagingException, TransformNofileException, TransformNodirException, TransformWaitTimeoutException {
		pdf2word(new File(Prop.get("srcdirpath")), new File(Prop.get("dstdirpath")));
	}
	
	// pdf试转word
	public void pdf2word_test(File srcDir, File dstDir) throws IOException, InterruptedException, MessagingException, TransformNofileException, TransformNodirException, TransformPdfEncryptException, TransformWaitTimeoutException {
		TransformInfoStater tfis = new TransformInfoStater("pdf2word_test",srcDir, dstDir, robotMngr);
		doBeforeTransform(tfis);
		Pdf2WordUtils.pdf2word_test(tfis);	
		doAfterTransform(tfis);
	}
	
	public void pdf2word_test() throws IOException, InterruptedException, MessagingException, TransformNofileException, TransformNodirException, TransformPdfEncryptException, TransformWaitTimeoutException {
		pdf2word_test(new File(Prop.get("srcdirpath")), new File(Prop.get("dstdirpath")));
	}
	
	
	
	// 批量pdf转mobi，用calibre
	public void pdf2mobi_bycalibre(File srcDir, File dstDir) throws IOException, InterruptedException, MessagingException, TransformWaitTimeoutException, TransformNofileException, TransformNodirException {	
		TransformInfoStater tfis = new TransformInfoStater("pdf2mobi_bycalibre",srcDir, dstDir, robotMngr);
		doBeforeTransform(tfis);
		Pdf2MobiUtils.pdf2mobi_bycalibre_batch(tfis);
		doAfterTransform(tfis);
	}
	
	// 批量pdf转mobi，用calibre
	public void pdf2mobi_bycalibre() throws IOException, InterruptedException, MessagingException, TransformWaitTimeoutException, TransformNofileException, TransformNodirException {
		pdf2mobi_bycalibre(new File(Prop.get("srcdirpath")), new File(Prop.get("dstdirpath")));
	}
	
	// 试转pdf转mobi
	public void pdf2mobi_bycalibre_test(File srcDir, File dstDir) throws IOException, InterruptedException, MessagingException, TransformWaitTimeoutException, TransformNofileException, TransformNodirException, TransformPdfEncryptException {
		TransformInfoStater tfis = new TransformInfoStater("pdf2mobi_bycalibre_test",srcDir, dstDir, robotMngr);
		doBeforeTransform(tfis);
		Pdf2MobiUtils.pdf2mobi_bycalibre_test(tfis);
		doAfterTransform(tfis);
	}
	
	// 试转pdf转mobi
	public void pdf2mobi_bycalibre_test() throws IOException, InterruptedException, MessagingException, TransformWaitTimeoutException, TransformNofileException, TransformNodirException, TransformPdfEncryptException {
		pdf2mobi_bycalibre_test(new File(Prop.get("srcdirpath")), new File(Prop.get("dstdirpath")));
	}
	
	// 批量txt转mobi，用calibre
	public void txt2mobi(File srcDir, File dstDir) throws IOException, InterruptedException, MessagingException, TransformWaitTimeoutException, TransformNofileException, TransformNodirException {
		TransformInfoStater tfis = new TransformInfoStater("txt2mobi",srcDir, dstDir, robotMngr);
		doBeforeTransform(tfis);
		Txt2MobiUtils.txt2mobi_batch(tfis);
		doAfterTransform(tfis);
	}
	
	public void txt2mobi() throws IOException, InterruptedException, MessagingException, TransformWaitTimeoutException, TransformNofileException, TransformNodirException {
		txt2mobi(new File(Prop.get("srcdirpath")), new File(Prop.get("dstdirpath")));
	}
	
	// 试转txt转mobi
	public void txt2mobi_test(File srcDir, File dstDir) throws IOException, InterruptedException, MessagingException, TransformWaitTimeoutException, TransformNofileException, TransformNodirException {
		TransformInfoStater tfis = new TransformInfoStater("txt2mobi_test",srcDir, dstDir, robotMngr);
		doBeforeTransform(tfis);
		Txt2MobiUtils.txt2mobi_test(tfis);
		doAfterTransform(tfis);
	}
	
	public void txt2mobi_test() throws IOException, InterruptedException, MessagingException, TransformWaitTimeoutException, TransformNofileException, TransformNodirException {
		txt2mobi_test(new File(Prop.get("srcdirpath")), new File(Prop.get("dstdirpath")));
	}
	
	// 批量img转word
	public void img2word(File srcDir, File dstDir) throws IOException, InterruptedException, MessagingException, TransformNofileException, TransformNodirException, TransformWaitTimeoutException {	
		TransformInfoStater tfis = new TransformInfoStater("img2word",srcDir, dstDir, robotMngr);
		doBeforeTransform(tfis);
		Img2WordUtils.img2word_batch(tfis);	
		doAfterTransform(tfis);
	}
	
	public void img2word() throws IOException, InterruptedException, MessagingException, TransformNofileException, TransformNodirException, TransformWaitTimeoutException {
		img2word(new File(Prop.get("srcdirpath")), new File(Prop.get("dstdirpath")));
	}
	
	// 试转img转word
	public void img2word_test(File srcDir, File dstDir) throws IOException, InterruptedException, MessagingException, TransformNofileException, TransformNodirException, TransformWaitTimeoutException {
		TransformInfoStater tfis = new TransformInfoStater("img2word_test",srcDir, dstDir, robotMngr);
		doBeforeTransform(tfis);
		Img2WordUtils.img2word_test(tfis);
		doAfterTransform(tfis);
	}
	
	public void img2word_test() throws IOException, InterruptedException, MessagingException, TransformNofileException, TransformNodirException, TransformWaitTimeoutException {
		img2word_test(new File(Prop.get("srcdirpath")), new File(Prop.get("dstdirpath")));
	}
	
	// pdf批量转epub
	public void pdf2epub(File srcDir, File dstDir) throws IOException, InterruptedException, MessagingException, TransformNofileException, TransformNodirException, TransformWaitTimeoutException {
		TransformInfoStater tfis = new TransformInfoStater("pdf2epub",srcDir, dstDir, robotMngr);
		doBeforeTransform(tfis);
		Pdf2EpubUtils.pdf2epub_batch(tfis);
		doAfterTransform(tfis);
	}
	
	public void pdf2epub() throws IOException, InterruptedException, MessagingException, TransformNofileException, TransformNodirException, TransformWaitTimeoutException {
		pdf2epub(new File(Prop.get("srcdirpath")), new File(Prop.get("dstdirpath")));
	}
	
	// 试转pdf转epub
	public void pdf2epub_test(File srcDir, File dstDir) throws IOException, InterruptedException, MessagingException, TransformNofileException, TransformNodirException, TransformPdfEncryptException, TransformWaitTimeoutException {
		TransformInfoStater tfis = new TransformInfoStater("pdf2epub_test",srcDir, dstDir, robotMngr);
		doBeforeTransform(tfis);
		Pdf2EpubUtils.pdf2epub_test(tfis);
		doAfterTransform(tfis);
	}
	
	public void pdf2epub_test() throws IOException, InterruptedException, MessagingException, TransformNofileException, TransformNodirException, TransformPdfEncryptException, TransformWaitTimeoutException {
		pdf2epub_test(new File(Prop.get("srcdirpath")), new File(Prop.get("dstdirpath")));
	}
	
	// 用abbyy进行pdf转mobi
	public void pdf2mobi_byabbyy(File srcDir, File dstDir) throws IOException, InterruptedException, MessagingException, TransformWaitTimeoutException, TransformNofileException, TransformNodirException {
		TransformInfoStater tfis = new TransformInfoStater("pdf2mobi_byabbyy",srcDir, dstDir, robotMngr);
		doBeforeTransform(tfis);
		Pdf2MobiUtils.pdf2mobi_byabbyy_batch(tfis);
		doAfterTransform(tfis);
	}
	
	public void pdf2mobi_byabbyy() throws IOException, InterruptedException, MessagingException, TransformWaitTimeoutException, TransformNofileException, TransformNodirException {
		pdf2mobi_byabbyy(new File(Prop.get("srcdirpath")), new File(Prop.get("dstdirpath")));
	}
	
	public void pdf2mobi_byabbyy_test(File srcDir, File dstDir) throws IOException, InterruptedException, MessagingException, TransformWaitTimeoutException, TransformNofileException, TransformNodirException, TransformPdfEncryptException {
		TransformInfoStater tfis = new TransformInfoStater("pdf2mobi_byabbyy_test",srcDir, dstDir, robotMngr);
		doBeforeTransform(tfis);
		Pdf2MobiUtils.pdf2mobi_byabbyy_test(tfis);
		doAfterTransform(tfis);	
	}
	
	public void pdf2mobi_byabbyy_test() throws IOException, InterruptedException, MessagingException, TransformWaitTimeoutException, TransformNofileException, TransformNodirException, TransformPdfEncryptException {
		pdf2mobi_byabbyy_test(new File(Prop.get("srcdirpath")), new File(Prop.get("dstdirpath")));
	}
	
	// 执行单个测试计划
	private void execSingleSelftestRule(SelftestRule rule) throws IOException, InterruptedException, MessagingException, TransformWaitTimeoutException, TransformNofileException, TransformNodirException, TransformPdfEncryptException{
		// 如果规则无效，则直接退出
		if(rule==null || !rule.isValid()) return;
		File[] oldFiles = new File(Prop.get("srcdirpath")).listFiles();
		for(File oldFile : oldFiles) {
			if(oldFile.exists() && oldFile.isFile()) {
				oldFile.delete();
			}
		}
		for(int i=0; i<rule.getTestCount(); i++){
			SelftestFilesPool.generateTestFilesByTransformType(rule.getTransformType(),rule.getFileCount());
			if(rule.getTransformType().equals("caj2pdf")){
				caj2pdf();
			}else if(rule.getTransformType().equals("caj2pdf_test")){
				caj2pdf_test();
			}else if(rule.getTransformType().equals("caj2word")){
				caj2word();
			}else if(rule.getTransformType().equals("caj2word_test")){
				caj2word_test();
			}else if(rule.getTransformType().equals("img2word")){
				img2word();
			}else if(rule.getTransformType().equals("img2word_test")){
				img2word_test();
			}else if(rule.getTransformType().equals("pdf2epub")){
				pdf2epub();
			}else if(rule.getTransformType().equals("pdf2epub_test")){
				pdf2epub_test();
			}else if(rule.getTransformType().equals("pdf2mobi_byabbyy")){
				pdf2mobi_byabbyy();
			}else if(rule.getTransformType().equals("pdf2mobi_byabbyy_test")){
				pdf2mobi_byabbyy_test();
			}else if(rule.getTransformType().equals("pdf2mobi_bycalibre")){
				pdf2mobi_bycalibre();
			}else if(rule.getTransformType().equals("pdf2mobi_bycalibre_test")){
				pdf2mobi_bycalibre_test();
			}else if(rule.getTransformType().equals("txt2mobi")){
				txt2mobi();
			}else if(rule.getTransformType().equals("txt2mobi_test")){
				txt2mobi_test();
			}else if(rule.getTransformType().equals("pdf2word")){
				pdf2word();
			}else if(rule.getTransformType().equals("pdf2word_test")){
				pdf2word_test();
			}
		}
	}
	
	// 自测模式
	public void selftest() throws IOException {
		selftest = true;
		List<SelftestRule> rules = SelftestRuleUtils.getSelftestRules();
		for (SelftestRule sr : rules) {
			try {
				execSingleSelftestRule(sr);
			} catch (Throwable e) {		// 单次执行出现任何问题都不影响其他测试
				e.printStackTrace();
			}
		}
		selftest = false;
	}
	
	// 下载QQ邮件中的附件
	public void download_qqmailfiles() throws IOException, ParseException {
		logger.warn("开始下载QQ邮件...");
		QQMailArchiveUtils.archive();
		String noticeContent = "下载QQ邮件成功，请回到电脑继续操作！";
		File dstDir = null;
		boolean exception = false;
		String imapserver = Prop.get("qqmail.popserver");
		String account = Prop.get("qqmail.account"); 
		String passwd = Prop.get("qqmail.passwd"); 
		String foldername = Prop.get("qqmail.foldername"); 
		String savefolder = Prop.get("qqmail.savefolder");
		try {
			dstDir = QQMailRcvUtils.rcvAllUnreadMails(imapserver, account, passwd, foldername, true, savefolder);
		} catch (Exception e) {
			e.printStackTrace();
			noticeContent = "下载QQ邮件失败，请自行手动下载QQ邮件！！！";
			exception = true;
		}finally {
			if(dstDir==null) noticeContent="没有需要下载的QQ邮件，请回到电脑继续操作！";
			doAfter(noticeContent,dstDir,exception);
		}
		logger.warn("完成下载QQ邮件...");
	}
	
	private void download_one_qqmailfiles() throws IOException, ParseException {
		logger.warn("开始下载一封含附件的QQ未读邮件...");
		Random random = new Random();
		// 取随机数，保证每10次有1次取归档邮件
		if((random.nextInt(5)+1)==3) {
			QQMailArchiveUtils.archive();
		}
		
		String noticeContent = "下载QQ邮件成功，请回到电脑继续操作！！！";
		File dstDir = null;
		boolean exception = false;
		try {
			dstDir = QQMailRcvUtils.rcvOneUnreadMailToSrcDir();
			if(dstDir!=null && dstDir.exists() && dstDir.isDirectory()) {
				// 检查是否有压缩文件，如果有，解压缩
				for(File file:dstDir.listFiles()) {
					if(UnZipUtils.canDeCompress(file.getCanonicalPath())) {
						UnZipUtils.deCompress(file.getCanonicalPath(), dstDir.getCanonicalPath());
					}
				}
				// 将目录下所有文件都集中到当前目录下
				FileUtils.gatherAllFiles(dstDir.getCanonicalPath());
			}
		}catch (Exception e) {
			logger.warn(ExceptionUtils.getStackTrace(e));
			noticeContent = "下载QQ邮件失败，请自行手动下载QQ邮件！！！";
			exception = true;
		}finally {
			if(dstDir!=null) {
				doAfter(noticeContent,dstDir.getParentFile(),exception);
				if(dstDir.exists()) {
					FileUtils.copyFileInSrcDirToDstDir(new File(Prop.get("bat.openfinishedwindows.dirpath")), dstDir);
				}
				FileUtils.copyFileInSrcDirToDstDir(new File(Prop.get("bat.dirpath")), dstDir.getParentFile());
			}else {
				doAfter(noticeContent,null,exception);
			}
			
		}
		logger.warn("结束下载一封含附件的QQ未读邮件...");
	}
	
	private void caj2word_sendmail(MailDir md) throws TransformNodirException, TransformWaitTimeoutException, TransformNofileException, IOException, InterruptedException, MessagingException {
		TransformInfoStater tfis = new TransformInfoStater("caj2word", md.getDir(), new File(Prop.get("dstdirpath")), robotMngr, new MailDateStrTransformDstDirGenerator());
		tfis.setNeedDelSrcDir(true);
		tfis.setNeedSendDstFileMail(true);
		tfis.setMaildir(md);
		tfis.setNeedCopyContentOnClipboard(true);
		tfis.setClipboardContent("您的文件已经转好发到您的邮箱了。");
		doBeforeTransform(tfis);
		Caj2WordUtils.caj2word_batch(tfis);
		doAfterTransform(tfis);
	}
	
	private void caj2word_sendmail() throws TransformNodirException, TransformWaitTimeoutException, TransformNofileException, IOException, InterruptedException, MessagingException {
		Set<MailDir> set = MailDir.scanAndGetMailDir(new File(Prop.get("srcdirpath")));
		for(MailDir md : set) {
			caj2word_sendmail(md);
		}
	}
	
	private void caj2word_test_sendmail() throws TransformNodirException, TransformNofileException, TransformWaitTimeoutException, IOException, InterruptedException, MessagingException, TransformPdfEncryptException {
		Set<MailDir> set = MailDir.scanAndGetMailDir(new File(Prop.get("srcdirpath")));
		for(MailDir md : set) {
			caj2word_test_sendmail(md);
		}
	}

	private void caj2word_test_sendmail(MailDir md) throws TransformNodirException, TransformNofileException, TransformWaitTimeoutException, IOException, InterruptedException, MessagingException, TransformPdfEncryptException {
		TransformInfoStater tfis = new TransformInfoStater("caj2word_test", md.getDir(), new File(Prop.get("dstdirpath")), robotMngr, new MailDateStrTransformDstDirGenerator());
		tfis.setNeedDelSrcDir(false);
		tfis.setNeedSendDstFileMail(true);
		tfis.setMaildir(md);
		tfis.setNeedCopyContentOnClipboard(true);
		tfis.setClipboardContent("您的文件试转效果已经转好发到您的邮箱了。");
		doBeforeTransform(tfis);
		Caj2WordUtils.caj2word_test(tfis);
		doAfterTransform(tfis);
	}
	
	private void caj2pdf_sendmail() throws TransformNodirException, TransformWaitTimeoutException, TransformNofileException, IOException, InterruptedException, MessagingException {
		Set<MailDir> set = MailDir.scanAndGetMailDir(new File(Prop.get("srcdirpath")));
		for(MailDir md : set) {
			caj2pdf_sendmail(md);
		}
	}

	private void caj2pdf_sendmail(MailDir md) throws TransformNodirException, TransformWaitTimeoutException, TransformNofileException, IOException, InterruptedException, MessagingException {
		TransformInfoStater tfis = new TransformInfoStater("caj2pdf", md.getDir(), new File(Prop.get("dstdirpath")), robotMngr, new MailDateStrTransformDstDirGenerator());
		tfis.setNeedDelSrcDir(true);
		tfis.setNeedSendDstFileMail(true);
		tfis.setMaildir(md);
		tfis.setNeedCopyContentOnClipboard(true);
		tfis.setClipboardContent("您的文件已经转好发到您的邮箱了。");
		doBeforeTransform(tfis);
		Caj2PdfUtils.caj2pdf_batch(tfis);
		doAfterTransform(tfis);
	}
	
	private void caj2pdf_test_sendmail() throws TransformNodirException, TransformWaitTimeoutException, TransformNofileException, IOException, InterruptedException, MessagingException, TransformPdfEncryptException {
		Set<MailDir> set = MailDir.scanAndGetMailDir(new File(Prop.get("srcdirpath")));
		for(MailDir md : set) {
			caj2pdf_test_sendmail(md);
		}
	}

	private void caj2pdf_test_sendmail(MailDir md) throws TransformNodirException, TransformWaitTimeoutException, TransformNofileException, IOException, InterruptedException, MessagingException, TransformPdfEncryptException {
		TransformInfoStater tfis = new TransformInfoStater("caj2pdf_test", md.getDir(), new File(Prop.get("dstdirpath")), robotMngr, new MailDateStrTransformDstDirGenerator());
		tfis.setNeedDelSrcDir(false);
		tfis.setNeedSendDstFileMail(true);
		tfis.setMaildir(md);
		tfis.setNeedCopyContentOnClipboard(true);
		tfis.setClipboardContent("您的文件试转效果已经转好发到您的邮箱了。");
		doBeforeTransform(tfis);
		Caj2PdfUtils.caj2pdf_test(tfis);
		doAfterTransform(tfis);
	}
	
	private void pdf2word_sendmail() throws TransformNodirException, TransformNofileException, IOException, InterruptedException, MessagingException, TransformWaitTimeoutException {
		Set<MailDir> set = MailDir.scanAndGetMailDir(new File(Prop.get("srcdirpath")));
		for(MailDir md : set) {
			pdf2word_sendmail(md);
		}
	}

	private void pdf2word_sendmail(MailDir md) throws TransformNodirException, TransformNofileException, IOException, InterruptedException, MessagingException, TransformWaitTimeoutException {
		TransformInfoStater tfis = new TransformInfoStater("pdf2word", md.getDir(), new File(Prop.get("dstdirpath")), robotMngr, new MailDateStrTransformDstDirGenerator());
		tfis.setNeedDelSrcDir(true);
		tfis.setNeedSendDstFileMail(true);
		tfis.setMaildir(md);
		tfis.setNeedCopyContentOnClipboard(true);
		tfis.setClipboardContent("您的文件已经转好发到您的邮箱了。");
		doBeforeTransform(tfis);
		Pdf2WordUtils.pdf2word_batch(tfis);
		doAfterTransform(tfis);
	}
	
	private void pdf2word_test_sendmail() throws TransformNofileException, TransformNodirException, IOException, InterruptedException, MessagingException, TransformPdfEncryptException, TransformWaitTimeoutException {
		Set<MailDir> set = MailDir.scanAndGetMailDir(new File(Prop.get("srcdirpath")));
		for(MailDir md : set) {
			pdf2word_test_sendmail(md);
		}
	}

	private void pdf2word_test_sendmail(MailDir md) throws TransformNofileException, IOException, InterruptedException, TransformNodirException, MessagingException, TransformPdfEncryptException, TransformWaitTimeoutException {
		TransformInfoStater tfis = new TransformInfoStater("pdf2word_test", md.getDir(), new File(Prop.get("dstdirpath")), robotMngr, new MailDateStrTransformDstDirGenerator());
		tfis.setNeedDelSrcDir(false);
		tfis.setNeedSendDstFileMail(true);
		tfis.setMaildir(md);
		tfis.setNeedCopyContentOnClipboard(true);
		tfis.setNeedDelMidFile(true);
		tfis.setClipboardContent("您的文件试转效果已经转好发到您的邮箱了。");
		doBeforeTransform(tfis);
		Pdf2WordUtils.pdf2word_test(tfis);
		doAfterTransform(tfis);
	}
	
	private void pdf2mobi_bycalibre_sendmail() throws TransformNofileException, TransformWaitTimeoutException, TransformNodirException, IOException, InterruptedException, MessagingException {
		Set<MailDir> set = MailDir.scanAndGetMailDir(new File(Prop.get("srcdirpath")));
		for(MailDir md : set) {
			pdf2mobi_bycalibre_sendmail(md);
		}
	}

	private void pdf2mobi_bycalibre_sendmail(MailDir md) throws TransformNofileException, TransformWaitTimeoutException, IOException, InterruptedException, TransformNodirException, MessagingException {
		TransformInfoStater tfis = new TransformInfoStater("pdf2mobi_bycalibre", md.getDir(), new File(Prop.get("dstdirpath")), robotMngr, new MailDateStrTransformDstDirGenerator());
		tfis.setNeedDelSrcDir(true);
		tfis.setNeedSendDstFileMail(true);
		tfis.setMaildir(md);
		tfis.setNeedCopyContentOnClipboard(true);
		tfis.setClipboardContent("您的文件已经转好发到您的邮箱了。");
		doBeforeTransform(tfis);
		Pdf2MobiUtils.pdf2mobi_bycalibre_batch(tfis);
		doAfterTransform(tfis);
	}
	
	private void pdf2mobi_bycalibre_test_sendmail() throws TransformNodirException, TransformNofileException, TransformWaitTimeoutException, IOException, InterruptedException, MessagingException, TransformPdfEncryptException {
		Set<MailDir> set = MailDir.scanAndGetMailDir(new File(Prop.get("srcdirpath")));
		for(MailDir md : set) {
			pdf2mobi_bycalibre_test_sendmail(md);
		}
	}

	private void pdf2mobi_bycalibre_test_sendmail(MailDir md) throws TransformNodirException, TransformNofileException, TransformWaitTimeoutException, IOException, InterruptedException, MessagingException, TransformPdfEncryptException {
		TransformInfoStater tfis = new TransformInfoStater("pdf2mobi_bycalibre_test", md.getDir(), new File(Prop.get("dstdirpath")), robotMngr, new MailDateStrTransformDstDirGenerator());
		tfis.setNeedDelSrcDir(false);
		tfis.setNeedSendDstFileMail(true);
		tfis.setMaildir(md);
		tfis.setNeedCopyContentOnClipboard(true);
		tfis.setNeedDelMidFile(true);
		tfis.setClipboardContent("您的文件试转效果已经转好发到您的邮箱了。");
		doBeforeTransform(tfis);
		Pdf2MobiUtils.pdf2mobi_bycalibre_test(tfis);
		doAfterTransform(tfis);
	}
	
	private void txt2mobi_sendmail() throws TransformNodirException, TransformNofileException, TransformWaitTimeoutException, IOException, InterruptedException, MessagingException {
		Set<MailDir> set = MailDir.scanAndGetMailDir(new File(Prop.get("srcdirpath")));
		for(MailDir md : set) {
			txt2mobi_sendmail(md);
		}
	}

	private void txt2mobi_sendmail(MailDir md) throws TransformNodirException, TransformNofileException, TransformWaitTimeoutException, IOException, InterruptedException, MessagingException {
		TransformInfoStater tfis = new TransformInfoStater("txt2mobi", md.getDir(), new File(Prop.get("dstdirpath")), robotMngr, new MailDateStrTransformDstDirGenerator());
		tfis.setNeedDelSrcDir(true);
		tfis.setNeedSendDstFileMail(true);
		tfis.setMaildir(md);
		tfis.setNeedCopyContentOnClipboard(true);
		tfis.setClipboardContent("您的文件已经转好发到您的邮箱了。");
		doBeforeTransform(tfis);
		Txt2MobiUtils.txt2mobi_batch(tfis);
		doAfterTransform(tfis);
	}
	
	private void txt2mobi_test_sendmail() throws TransformNofileException, TransformWaitTimeoutException, TransformNodirException, IOException, InterruptedException, MessagingException {
		Set<MailDir> set = MailDir.scanAndGetMailDir(new File(Prop.get("srcdirpath")));
		for(MailDir md : set) {
			txt2mobi_test_sendmail(md);
		}
	}

	private void txt2mobi_test_sendmail(MailDir md) throws TransformNofileException, TransformWaitTimeoutException, IOException, InterruptedException, TransformNodirException, MessagingException {
		TransformInfoStater tfis = new TransformInfoStater("txt2mobi_test", md.getDir(), new File(Prop.get("dstdirpath")), robotMngr, new MailDateStrTransformDstDirGenerator());
		tfis.setNeedDelSrcDir(false);
		tfis.setNeedSendDstFileMail(true);
		tfis.setMaildir(md);
		tfis.setNeedCopyContentOnClipboard(true);
		tfis.setNeedDelMidFile(true);
		tfis.setClipboardContent("您的文件试转效果已经转好发到您的邮箱了。");
		doBeforeTransform(tfis);
		Txt2MobiUtils.txt2mobi_test(tfis);
		doAfterTransform(tfis);
	}
	
	private void img2word_sendmail() throws TransformNodirException, TransformNofileException, IOException, InterruptedException, MessagingException, TransformWaitTimeoutException {
		Set<MailDir> set = MailDir.scanAndGetMailDir(new File(Prop.get("srcdirpath")));
		for(MailDir md : set) {
			img2word_sendmail(md);
		}
	}

	private void img2word_sendmail(MailDir md) throws TransformNodirException, TransformNofileException, IOException, InterruptedException, MessagingException, TransformWaitTimeoutException {
		TransformInfoStater tfis = new TransformInfoStater("img2word", md.getDir(), new File(Prop.get("dstdirpath")), robotMngr, new MailDateStrTransformDstDirGenerator());
		tfis.setNeedDelSrcDir(true);
		tfis.setNeedSendDstFileMail(true);
		tfis.setMaildir(md);
		tfis.setNeedCopyContentOnClipboard(true);
		tfis.setClipboardContent("您的文件已经转好发到您的邮箱了。");
		doBeforeTransform(tfis);
		Img2WordUtils.img2word_batch(tfis);	
		doAfterTransform(tfis);
	}
	
	private void img2word_test_sendmail() throws TransformNodirException, TransformNofileException, IOException, InterruptedException, MessagingException, TransformWaitTimeoutException {
		Set<MailDir> set = MailDir.scanAndGetMailDir(new File(Prop.get("srcdirpath")));
		for(MailDir md : set) {
			img2word_test_sendmail(md);
		}
	}

	private void img2word_test_sendmail(MailDir md) throws TransformNodirException, TransformNofileException, IOException, InterruptedException, MessagingException, TransformWaitTimeoutException {
		TransformInfoStater tfis = new TransformInfoStater("img2word_test", md.getDir(), new File(Prop.get("dstdirpath")), robotMngr, new MailDateStrTransformDstDirGenerator());
		tfis.setNeedDelSrcDir(false);
		tfis.setNeedSendDstFileMail(true);
		tfis.setMaildir(md);
		tfis.setNeedCopyContentOnClipboard(true);
		tfis.setClipboardContent("您的文件试转效果已经转好发到您的邮箱了。");
		doBeforeTransform(tfis);
		Img2WordUtils.img2word_test(tfis);
		doAfterTransform(tfis);
	}
	
	private void pdf2epub_sendmail() throws TransformNodirException, TransformNofileException, IOException, InterruptedException, MessagingException, TransformWaitTimeoutException {
		Set<MailDir> set = MailDir.scanAndGetMailDir(new File(Prop.get("srcdirpath")));
		for(MailDir md : set) {
			pdf2epub_sendmail(md);
		}
	}

	private void pdf2epub_sendmail(MailDir md) throws TransformNodirException, TransformNofileException, IOException, InterruptedException, MessagingException, TransformWaitTimeoutException {
		TransformInfoStater tfis = new TransformInfoStater("pdf2epub", md.getDir(), new File(Prop.get("dstdirpath")), robotMngr, new MailDateStrTransformDstDirGenerator());
		tfis.setNeedDelSrcDir(true);
		tfis.setNeedSendDstFileMail(true);
		tfis.setMaildir(md);
		tfis.setNeedCopyContentOnClipboard(true);
		tfis.setClipboardContent("您的文件已经转好发到您的邮箱了。");
		doBeforeTransform(tfis);
		Pdf2EpubUtils.pdf2epub_batch(tfis);
		doAfterTransform(tfis);
	}
	
	private void pdf2epub_test_sendmail() throws TransformNodirException, TransformNofileException, IOException, InterruptedException, MessagingException, TransformPdfEncryptException, TransformWaitTimeoutException {
		Set<MailDir> set = MailDir.scanAndGetMailDir(new File(Prop.get("srcdirpath")));
		for(MailDir md : set) {
			pdf2epub_test_sendmail(md);
		}
	}

	private void pdf2epub_test_sendmail(MailDir md) throws TransformNodirException, TransformNofileException, IOException, InterruptedException, MessagingException, TransformPdfEncryptException, TransformWaitTimeoutException {
		TransformInfoStater tfis = new TransformInfoStater("pdf2epub_test", md.getDir(), new File(Prop.get("dstdirpath")), robotMngr, new MailDateStrTransformDstDirGenerator());
		tfis.setNeedDelSrcDir(false);
		tfis.setNeedSendDstFileMail(true);
		tfis.setMaildir(md);
		tfis.setNeedCopyContentOnClipboard(true);
		tfis.setNeedDelMidFile(true);
		tfis.setClipboardContent("您的文件试转效果已经转好发到您的邮箱了。");
		doBeforeTransform(tfis);
		Pdf2EpubUtils.pdf2epub_test(tfis);
		doAfterTransform(tfis);
	}
	
	private void pdf2mobi_byabbyy_sendmail() throws TransformNodirException, TransformNofileException, TransformWaitTimeoutException, IOException, InterruptedException, MessagingException {
		Set<MailDir> set = MailDir.scanAndGetMailDir(new File(Prop.get("srcdirpath")));
		for(MailDir md : set) {
			pdf2mobi_byabbyy_sendmail(md);
		}
	}
	
	private void pdf2mobi_byabbyy_sendmail(MailDir md) throws TransformNodirException, TransformNofileException, TransformWaitTimeoutException, IOException, InterruptedException, MessagingException {
		TransformInfoStater tfis = new TransformInfoStater("pdf2mobi_byabbyy", md.getDir(), new File(Prop.get("dstdirpath")), robotMngr, new MailDateStrTransformDstDirGenerator());
		tfis.setNeedDelSrcDir(true);
		tfis.setNeedSendDstFileMail(true);
		tfis.setMaildir(md);
		tfis.setNeedCopyContentOnClipboard(true);
		tfis.setClipboardContent("您的文件已经转好发到您的邮箱了。");
		doBeforeTransform(tfis);
		Pdf2MobiUtils.pdf2mobi_byabbyy_batch(tfis);
		doAfterTransform(tfis);
	}

	private void pdf2mobi_byabbyy_test_sendmail() throws TransformNodirException, TransformNofileException, TransformWaitTimeoutException, IOException, InterruptedException, MessagingException, TransformPdfEncryptException {
		Set<MailDir> set = MailDir.scanAndGetMailDir(new File(Prop.get("srcdirpath")));
		for(MailDir md : set) {
			pdf2mobi_byabbyy_test_sendmail(md);
		}
	}

	private void pdf2mobi_byabbyy_test_sendmail(MailDir md) throws TransformNodirException, TransformNofileException, TransformWaitTimeoutException, IOException, InterruptedException, MessagingException, TransformPdfEncryptException {
		TransformInfoStater tfis = new TransformInfoStater("pdf2mobi_byabbyy_test", md.getDir(), new File(Prop.get("dstdirpath")), robotMngr, new MailDateStrTransformDstDirGenerator());
		tfis.setNeedDelSrcDir(false);
		tfis.setNeedSendDstFileMail(true);
		tfis.setMaildir(md);
		tfis.setNeedCopyContentOnClipboard(true);
		tfis.setNeedDelMidFile(true);
		tfis.setClipboardContent("您的文件试转效果已经转好发到您的邮箱了。");
		doBeforeTransform(tfis);
		Pdf2MobiUtils.pdf2mobi_byabbyy_test(tfis);
		doAfterTransform(tfis);
	}
	
	private void sendmail(MailFileType mailFileType) throws TransformNodirException, IOException, MessagingException, TransformNofileException {
		Set<MailDir> set = MailDir.scanAndGetSendMailDir(new File(Prop.get("sendmail.srcdirpath")));
		for(MailDir md : set) {
			sendmail(md,mailFileType);
		}
	}

	private void sendmail(MailDir md, MailFileType mailFileType) throws TransformNodirException, IOException, MessagingException, TransformNofileException {
		String transformType = "sendmail_"+mailFileType.toString().toLowerCase();
		TransformInfoStater tfis = new TransformInfoStater(transformType, md.getDir(), new File(Prop.get("sendmail.dstdirpath")),  robotMngr, new MailDateStrTransformDstDirGenerator());
		tfis.setMaildir(md);
		tfis.setNeedSendDstFileMail(false);
		tfis.setNeedNoticeMail(false);
		//tfis.setNeedDelSrcDir(true);
		//tfis.setNeedSaveDb(false);
		tfis.setNeedOpenFinisedWindows(false);
		doBeforeTransform(tfis);
		if(!tfis.hasFileToTransform()) {
			tfis.setErrorMsg("目录里没有要发送的文件");
			throw new TransformNofileException();
		}
		for(File file : tfis.getQualifiedSrcFileSet()){
			tfis.addDstFile(file);
			logger.warn("发送文件："+file.getName());
		}
		QQMailSendUtils.sendMailWithFile(tfis);
		doAfterTransform(tfis);
	}

	// 空闲时工作
	private void idleWork() throws IOException, TransformWaitTimeoutException, InterruptedException, TransformNodirException {
		logger.warn("开始进行空闲时转换工作，工作随时可以结束");
		File srcDirParentDir = new File(Prop.get("idlework.srcdirpath"));
		if(srcDirParentDir.exists() && srcDirParentDir.isDirectory() 
				&& srcDirParentDir.listFiles()!=null && srcDirParentDir.listFiles().length>0) {
			for(File typeSrcDir : srcDirParentDir.listFiles()) {
				if(typeSrcDir.isDirectory()) {
					TransformType transformType  = TransformType.str2type(typeSrcDir.getName());
					if(transformType!=null) {
						for(File srcDir : typeSrcDir.listFiles()) {
							idleWork(srcDir,transformType);
						}
					}
				}
			}
		}
		logger.warn("已完成所有空闲时转换工作");
	}
	
	private void idleWork(File srcDir, TransformType transformType) throws IOException, TransformWaitTimeoutException, InterruptedException, TransformNodirException {	
		TransformInfoStater tfis = new TransformInfoStater("caj2word_idlework", srcDir, srcDir,  robotMngr, new DateStrTransformDstDirGenerator());
		doBeforeTransform(tfis);
		logger.warn("开始空闲时转换本文件夹下内容："+srcDir.getCanonicalPath());
		FileUtils.deleteHiddenFiles(srcDir);
		File[] files = srcDir.listFiles();
		String transformRecordFileName = Prop.get("idlework.transformrecordfilename");
		File transformRecordFile = new File(srcDir, transformRecordFileName);
		Set<String> finishFileNameSet = new HashSet<String>();
		if(transformRecordFile.exists()) {
			finishFileNameSet = FileUtils.readFileContentPerLineEncoding(transformRecordFile.getCanonicalPath());
		}
		for(File file: files) {
			if(transformRecordFileName.equalsIgnoreCase(file.getName())) {	// 是记录文件就跳过。
				logger.warn("记录文件不转换:"+file.getCanonicalPath());
				continue;
			}
			if(finishFileNameSet.contains(file.getCanonicalPath())) {				// 已经转过了就跳过
				logger.warn("转换过的不转换:"+file.getCanonicalPath());
				continue;
			}
			if(TransformType.caj2word.equals(transformType)) {
				tfis.addSrcFile(file);
				TransformFileSetEx transformFileSetEx = Caj2WordUtils.caj2word_single(file, robotMngr);
				if(transformFileSetEx.isSuccess()) {
					for (File finishedFile : transformFileSetEx.getSrcFile()) {
						saveFileName2TransformRecordFile(finishedFile, transformRecordFile);
						for(File dstFile : transformFileSetEx.getDstFile()) {
							tfis.addDstFile(dstFile);
						}
						AsyncDbSaver.saveDb(tfis);
					}
				}
			}else if(TransformType.pdf2word.equals(transformType)) {
				tfis.getInfo().setTransform_type("pdf2word_idlework");
				tfis.addSrcFile(file);
				TransformFileSetEx transformFileSetEx = Pdf2WordUtils.pdf2word_single(file, robotMngr);
				if(transformFileSetEx.isSuccess()) {
					for (File finishedFile : transformFileSetEx.getSrcFile()) {
						saveFileName2TransformRecordFile(finishedFile, transformRecordFile);	
						for(File dstFile : transformFileSetEx.getDstFile()) {
							tfis.addDstFile(dstFile);
						}
						AsyncDbSaver.saveDb(tfis);
					}
				}
			}else if(TransformType.pdf2mobi_byabbyy.equals(transformType)) {
				tfis.getInfo().setTransform_type("pdf2mobi_byabbyy_idlework");
				tfis.addSrcFile(file);
				TransformFileSetEx transformFileSetEx = Pdf2MobiUtils.pdf2mobi_byabbyy_single(file, robotMngr);
				if(transformFileSetEx.isSuccess()) {
					for (File finishedFile : transformFileSetEx.getSrcFile()) {
						saveFileName2TransformRecordFile(finishedFile, transformRecordFile);	
						for(File dstFile : transformFileSetEx.getDstFile()) {
							tfis.addDstFile(dstFile);
						}
						AsyncDbSaver.saveDb(tfis);
					}
				}
			}
		}
		
		logger.warn("结束空闲时转换本文件夹下内容："+srcDir.getCanonicalPath());
	}
	
	

	private void saveFileName2TransformRecordFile(File file, File transformRecordFile) throws IOException {
		String content = file.getCanonicalPath();
		FileUtils.writeLineToFile(transformRecordFile.getCanonicalPath(), content, true);
	}
	
	private void html2pdf_sendmail() throws TransformNodirException, IOException, MessagingException, TransformNofileException, TransformWaitTimeoutException, InterruptedException {
		Set<MailDir> set = MailDir.scanAndGetMailDir(new File(Prop.get("srcdirpath")));
		for(MailDir md : set) {
			html2pdf_sendmail(md);
		}
	}

	private void html2pdf_sendmail(MailDir md) throws TransformNodirException, IOException, MessagingException, TransformNofileException, TransformWaitTimeoutException, InterruptedException {
		TransformInfoStater tfis = new TransformInfoStater("html2pdf", md.getDir(), new File(Prop.get("dstdirpath")), robotMngr, new MailDateStrTransformDstDirGenerator());
		tfis.setNeedDelSrcDir(true);
		tfis.setNeedSendDstFileMail(true);
		tfis.setMaildir(md);
		tfis.setNeedCopyContentOnClipboard(true);
		tfis.setClipboardContent("您的文件已经转好发到您的邮箱了。");
		doBeforeTransform(tfis);
		Html2PdfUtils.html2pdf_batch(tfis);
		doAfterTransform(tfis);
	}

	public void execTransform(String[] args) throws IOException, InterruptedException, MessagingException, TransformWaitTimeoutException, TransformNofileException, TransformNodirException, ParseException, TransformPdfEncryptException {
		String method = Prop.get("caj2pdf.start");
		if(method==null) {
			method = "caj2word";
		}
		if(args.length>=1) {
			method = args[0].trim(); 
		}
		if("caj2word_sendmail".equalsIgnoreCase(method)) {
			caj2word_sendmail();
		}else if("caj2word_test_sendmail".equalsIgnoreCase(method)) {
			caj2word_test_sendmail();
		}else if("caj2pdf_sendmail".equalsIgnoreCase(method)) {
			caj2pdf_sendmail();
		}else if("caj2pdf_test_sendmail".equalsIgnoreCase(method)) {
			caj2pdf_test_sendmail();
		}else if("pdf2word_sendmail".equalsIgnoreCase(method)) {
			pdf2word_sendmail();
		}else if("pdf2word_test_sendmail".equalsIgnoreCase(method)) {
			pdf2word_test_sendmail();
		}else if("html2pdf_sendmail".equalsIgnoreCase(method)) {
			html2pdf_sendmail();
		}else if("pdf2mobi_bycalibre_sendmail".equalsIgnoreCase(method)) {
			pdf2mobi_bycalibre_sendmail();
		}else if("pdf2mobi_bycalibre_test_sendmail".equalsIgnoreCase(method)) {
			pdf2mobi_bycalibre_test_sendmail();
		}else if("txt2mobi_sendmail".equalsIgnoreCase(method)) {
			txt2mobi_sendmail();
		}else if("txt2mobi_test_sendmail".equalsIgnoreCase(method)) {
			txt2mobi_test_sendmail();
		}else if("img2word_sendmail".equalsIgnoreCase(method)) {
			img2word_sendmail();
		}else if("img2word_test_sendmail".equalsIgnoreCase(method)) {
			img2word_test_sendmail();
		}else if("pdf2epub_sendmail".equalsIgnoreCase(method)) {
			pdf2epub_sendmail();
		}else if("pdf2epub_test_sendmail".equalsIgnoreCase(method)) {
			pdf2epub_test_sendmail();
		}else if("pdf2mobi_byabbyy_sendmail".equalsIgnoreCase(method)) {
			pdf2mobi_byabbyy_sendmail();
		}else if("pdf2mobi_byabbyy_test_sendmail".equalsIgnoreCase(method)) {
			pdf2mobi_byabbyy_test_sendmail();
		}else if("caj2word".equalsIgnoreCase(method)) {
			caj2word();
		}else if("caj2word_test".equalsIgnoreCase(method)) {
			caj2word_test();
		}else if("caj2pdf".equalsIgnoreCase(method)) {
			caj2pdf();
		}else if("caj2pdf_test".equalsIgnoreCase(method)) {
			caj2pdf_test();
		}else if("pdf2word".equalsIgnoreCase(method)) {
			pdf2word();
		}else if("pdf2word_test".equalsIgnoreCase(method)) {
			pdf2word_test();
		}else if("pdf2mobi_bycalibre".equalsIgnoreCase(method)) {
			pdf2mobi_bycalibre();
		}else if("pdf2mobi_bycalibre_test".equalsIgnoreCase(method)) {
			pdf2mobi_bycalibre_test();
		}else if("txt2mobi".equalsIgnoreCase(method)) {
			txt2mobi();
		}else if("txt2mobi_test".equalsIgnoreCase(method)) {
			txt2mobi_test();
		}else if("img2word".equalsIgnoreCase(method)) {
			img2word();
		}else if("img2word_test".equalsIgnoreCase(method)) {
			img2word_test();
		}else if("pdf2epub".equalsIgnoreCase(method)) {
			pdf2epub();
		}else if("pdf2epub_test".equalsIgnoreCase(method)) {
			pdf2epub_test();
		}else if("pdf2mobi_byabbyy".equalsIgnoreCase(method)) {
			pdf2mobi_byabbyy();
		}else if("pdf2mobi_byabbyy_test".equalsIgnoreCase(method)) {
			pdf2mobi_byabbyy_test();
		}else if("selftest".equalsIgnoreCase(method)) {
			selftest();
		}else if("download_qqmailfiles".equalsIgnoreCase(method)) {
			download_qqmailfiles();
		}else if("download_one_qqmailfiles".equalsIgnoreCase(method)) {
			download_one_qqmailfiles();
		}else if("captureimage".equalsIgnoreCase(method)) {
			if(args.length>=6) {
				Thread.sleep(Integer.parseInt(args[1])*1000);
				Common.captureImageAndSave(robotMngr, Integer.parseInt(args[2]), Integer.parseInt(args[3]), Integer.parseInt(args[4]), Integer.parseInt(args[5]));
			}
		}else if("tasklist".equalsIgnoreCase(method)) {
			String dstDir = Prop.get("tasklist.savedirpath");
			String filename = Prop.get("tasklist.filename");
			if(args.length>=2) {
				dstDir = args[1].trim();
			}
			if(args.length>=3) {
				filename = args[2].trim();
			}
			CmdExecutor.getSingleInstance().exportTasklistToFile(new File(dstDir, filename));
		}else if("sendmail_pdf".equalsIgnoreCase(method)) {
			sendmail(MailFileType.PDF);
		}else if("sendmail_word".equalsIgnoreCase(method)) {
			sendmail(MailFileType.WORD);
		}else if("sendmail_mobi".equalsIgnoreCase(method)) {
			sendmail(MailFileType.MOBI);
		}else if("sendmail_excel".equalsIgnoreCase(method)) {
			sendmail(MailFileType.EXCEL);
		}else if("sendmail_img".equalsIgnoreCase(method)) {
			sendmail(MailFileType.IMG);
		}else if("idlework".equalsIgnoreCase(method)) {
			idleWork();
		}
	}



	public static void main(String[] args) throws AWTException, IOException, InterruptedException, MessagingException, TransformWaitTimeoutException, TransformNofileException, TransformNodirException, ParseException, TransformPdfEncryptException {
		DocFormatConverter dfc = new DocFormatConverter();
		if(Prop.getBool("debug")) {		// 调试模式
			//dfc.download_one_qqmailfiles();
			//dfc.caj2word_test_sendmail();
			//dfc.selftest();
			//dfc.sendmail(MailFileType.WORD);
			dfc.caj2word_sendmail();
			//dfc.pdf2word_test();
		}else {
			/*dfc.download_one_qqmailfiles();
			if(1==1) {
				return;
			}*/
			try {
				dfc.execTransform(args);
			} catch (TransformWaitTimeoutException e) {
				e.printStackTrace();
				// 声音告警
				Common.noticeAlertSound();
				// 邮件告警
				Common.noticeAlertMail("转换错误：转换等待时间过长！！！");
			}catch (TransformNofileException e) {
				e.printStackTrace();
				// 声音告警
				Common.noticeAlertSound();
				String content = "转换错误：文件夹里没有要转换的文件！！！";
				if(e.getMessage()!=null) {
					content = "转换错误："+e.getMessage();
				}
				// 邮件告警
				Common.noticeAlertMail(content);
			} catch (TransformNodirException e) {
				e.printStackTrace();
				// 声音告警
				Common.noticeAlertSound();
				// 邮件告警
				Common.noticeAlertMail("转换错误:文件夹不存在！");
			} catch (TransformPdfEncryptException e) {
				e.printStackTrace();
				// 声音告警
				Common.noticeAlertSound();
				// 邮件告警
				Common.noticeAlertMail("转换错误:PDF要先解密！");
			}catch(Exception e) {
				e.printStackTrace();
				logger.warn(e.getStackTrace());
			}
		}
	}
}
