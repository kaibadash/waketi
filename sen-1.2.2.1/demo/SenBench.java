/*
 * SenBench.java - measures performance of Sen
 * 
 * Copyright (C) 2004 Sen Project
 * Masanori Harada <harada@ingrid.org>
 * 
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307
 * USA
 * 
 */

import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Locale;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import net.java.sen.StringTagger;
import net.java.sen.Token;

class SenBench {
    private static Log log = LogFactory.getLog(SenBench.class);
    private static String encoding =
        System.getProperty("bench.encoding", "JISAutoDetect");
    private static int repeat =
        Integer.parseInt(System.getProperty("bench.repeat", "1"));

    private static void doWork(StringTagger tagger, String text)
        throws Exception {
        Token[] tokens = tagger.analyze(text);
    }

    public static void main(String[] args) {
        try {
            if (args.length == 0) {
                System.out.println("usage: java SenBench file [file ..]");
                System.exit(2);
            }

            StringTagger tagger = StringTagger.getInstance(Locale.JAPANESE);

            long processed = 0;
            long nbytes = 0;
            long nchars = 0;

            long start = System.currentTimeMillis();
            for (int a = 0; a < args.length; a++) {
                String text = "";
                try {
                    RandomAccessFile raf = new RandomAccessFile(args[a], "r");
                    byte[] buf = new byte[(int) raf.length()];
                    raf.readFully(buf);
                    raf.close();
                    text = new String(buf, encoding);
                    nbytes += buf.length;
                    nchars += text.length();
                } catch (IOException ioe) {
                    log.error(ioe);
                    continue;
                }

                long s_start = System.currentTimeMillis();
                for (int c = 0; c < repeat; c++)
                    doWork(tagger, text);
                long s_end = System.currentTimeMillis();
                processed += (s_end - s_start);
            }
            long end = System.currentTimeMillis();
            System.out.println("number of files: " + args.length);
            System.out.println("number of repeat: " + repeat);
            System.out.println("number of bytes: " + nbytes);
            System.out.println("number of chars: " + nchars);
            System.out.println("total time elapsed: " + (end - start) + " msec.");
            System.out.println("analysis time: " + (processed) + " msec.");
        } catch (Exception e) {
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }
}
