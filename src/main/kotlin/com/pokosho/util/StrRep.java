package com.pokosho.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pokosho.PokoshoException;

/**
 * 文字列変換.
 * @author kaiba
 */
public class StrRep {
	private static Logger log = LoggerFactory.getLogger(StrRep.class);
	private Hashtable<String,Pattern> pattens;
	public StrRep(String repStrPath) throws PokoshoException {
		pattens = new Hashtable<String, Pattern>();
		File file = null;
		FileReader filereader = null;
		BufferedReader br = null;
		try {
			file = new File(repStrPath);
			filereader = new FileReader(file);
			br = new BufferedReader(filereader);
			String line = null;
			while ((line = br.readLine()) != null) {
				log.debug("repstr line:" + line);
				addToPatters(line);
			}
		} catch (IOException e) {
			throw new PokoshoException(e);
		} finally {
			try {
				if (br != null)	br.close();
				if (filereader != null) filereader.close();
			} catch (Exception e) {
				throw new PokoshoException(e);
			}
		}
	}

	public String rep(String org) {
		Enumeration<String> keys = pattens.keys();
		String res = org;
		log.debug("original:" + org);
		while (keys.hasMoreElements()) {
			String key = keys.nextElement();
			Pattern p = pattens.get(key);
			Matcher mat = p.matcher(org);
			res = mat.replaceAll(key);
		}
		log.debug("replaced:" + res);
		return res;
	}

	private void addToPatters(String line) {
		String[] splited = line.split("\t");
		String[] targets = splited[1].split(",");
		StringBuilder sb = new StringBuilder();

		sb.append("(" + targets[0] + ")");
		for (int i = 1; i < targets.length; i++) {
			sb.append("|(" + targets[i] + ")");
		}
		log.debug("regex str:" + sb.toString());
		pattens.put(splited[0], Pattern.compile(sb.toString()));
	}
}
