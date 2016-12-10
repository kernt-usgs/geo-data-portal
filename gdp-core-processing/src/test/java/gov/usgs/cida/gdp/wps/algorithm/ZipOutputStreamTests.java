package gov.usgs.cida.gdp.wps.algorithm;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.Test;

public class ZipOutputStreamTests {

	protected void writeTestfile(ZipOutputStream zip, String filename, String content) throws IOException{
		zip.putNextEntry(new ZipEntry(filename)); 
		zip.write(content.getBytes());
		zip.closeEntry();
	}
	
	@Test
	public void testZipDirectories_this_is_correct() throws Exception {
		
		File file = File.createTempFile("test",".zip");
		System.out.println(file.getAbsolutePath());
		
		try( FileOutputStream   fos    = new FileOutputStream(file);
			 ZipOutputStream    zip    = new ZipOutputStream(fos);
				) {
						
			writeTestfile(zip,"test0.txt", "content in a file outside dir");
			writeTestfile(zip,"dir/test1.txt", "content in first file within dir");
			writeTestfile(zip,"dir/test2.txt", "content in second file within dir");
			writeTestfile(zip,"test3.txt", "content in thrid file outside dir");
		}
		file.deleteOnExit();
		
	}

	@Test
	public void testZipDirectories_this_is_wrong() throws Exception {
		
		File file = File.createTempFile("test",".zip");
		System.out.println(file.getAbsolutePath());
		
		try( FileOutputStream   fos    = new FileOutputStream(file);
			 ZipOutputStream    zip    = new ZipOutputStream(fos);
				) {
			
			writeTestfile(zip,"test0.txt", "content in a file outside dir");
			
			zip.putNextEntry(new ZipEntry("dir/")); // THIS CREATES AN EMPTY DIR
			writeTestfile(zip,"test1.txt", "content in first file within dir"); // THIS IS NOT IN THE DIR
			writeTestfile(zip,"test2.txt", "content in second file within dir");
			zip.closeEntry(); // dir
			
			writeTestfile(zip,"test3.txt", "content in thrid file outside dir");
		}
		
		file.deleteOnExit();
		
	}
	
}
