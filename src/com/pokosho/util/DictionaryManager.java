package com.pokosho.util;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.regex.Pattern;

public class DictionaryManager {
	private static final String TITLE_FILE_PATH = "dict/jawiki-latest-all-titles-in-ns0";
	public static final String DICT_FILE_PATH = "dict/jawiki-latest-all-titles-in-ns0.csv";
	private static final String[] SKIP_WORDS = new String[] { ",", ")", "_",
			"\"", "#", "♥", "＃", "*" };

	public void createDictionary() throws IOException {
		File wikipediaTitleFile = null;
		File dictFile = null;
		Pattern pattern = Pattern.compile("[가-힟]");

		// 1288 名詞,固有名詞,一般,*,*,*,*
		// 1289 名詞,固有名詞,人名,一般,*,*,*
		try (FileReader filereader = new FileReader(wikipediaTitleFile);
				BufferedReader br = new BufferedReader(filereader);
				FileWriter fileWriter = new FileWriter(dictFile);
				BufferedWriter bw = new BufferedWriter(fileWriter);) {
			wikipediaTitleFile = new File(TITLE_FILE_PATH);
			dictFile = new File(DICT_FILE_PATH);
			String title = br.readLine(); // skip first
			// 「わけち」追加
			String dictLine = "わけち" + ",1289,1289,4000,名詞,一般,*,*,*,*," + "わけち"
					+ "," + "わけち" + "," + "わけち" + "\n";
			bw.write(dictLine);
			dictLine = "waketi" + ",1289,1289,4000,名詞,一般,*,*,*,*," + "わけち"
					+ "," + "わけち" + "," + "わけち" + "\n";
			bw.write(dictLine);
			while ((title = br.readLine()) != null) {
				boolean skip = false;
				for (String skipStr : SKIP_WORDS) {
					if (title.length() <= 1 || title.contains(skipStr)) {
						skip = true;
						break;
					}
				}
				if (!skip) {
					if (pattern.matcher(title).find()) {
						skip = true;
						System.out.println("skiped(regexp):" + title);
						continue;
					}
				}
				if (skip) {
					System.out.println("skiped:" + title);
					continue;
				}
				// 川竹,1285,1285,5622,名詞,一般,*,*,*,*,川竹,カワタケ,カワタケ
				dictLine = title + ",1288,1288,4000,名詞,一般,*,*,*,*," + title
						+ "," + "*" + "," + "*" + "\n";
				bw.write(dictLine);

			}
			while (title != null)
				;
		}
	}

	public static void main(String[] args) {
		DictionaryManager mng = new DictionaryManager();
		try {
			mng.createDictionary();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
