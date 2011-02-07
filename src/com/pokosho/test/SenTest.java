package com.pokosho.test;

import net.java.sen.StringTagger;
import net.java.sen.Token;

public class SenTest {
	public SenTest() {
		System.setProperty("sen.home","C:/usr/local/sen-1.2.2.1");
	}

	public void test(String str) throws Exception {
		StringTagger tagger = StringTagger.getInstance();
		Token[] token = tagger.analyze(str);
		for(int i=0; i<token.length; i++){
            System.out.println(token[i].getBasicString()
            	+"("+token[i].getTermInfo()+")");
        }
	}

	public static void main(String[] args) {
		SenTest s = new SenTest();
		try {
			s.test("東京に行ったら東京特許許可局に行きたい。");
		} catch (Exception e) {
			// TODO 自動生成された catch ブロック
			e.printStackTrace();
		}
	}
}
