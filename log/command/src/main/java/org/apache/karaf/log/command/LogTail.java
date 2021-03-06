/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.karaf.log.command;

import java.io.IOException;
import java.io.PrintStream;
import java.util.concurrent.*;

import org.apache.karaf.shell.commands.Command;
import org.ops4j.pax.logging.spi.PaxAppender;
import org.ops4j.pax.logging.spi.PaxLoggingEvent;

@Command(scope = "log", name = "tail", description = "Continuously display log entries. Use ctrl-c to quit this command")
public class LogTail extends DisplayLog {

    private ExecutorService executorService = Executors.newSingleThreadExecutor();
    
    protected Object doExecute() throws Exception {
        PrintEventThread printThread = new PrintEventThread();
        ReadKeyBoardThread readKeyboardThread = new ReadKeyBoardThread(Thread.currentThread());

        executorService.execute(printThread);
        executorService.execute(readKeyboardThread);

        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(200);
            } catch (java.lang.InterruptedException e) {
                break;
            }
        }
        printThread.abort();
        readKeyboardThread.abort();
        executorService.shutdownNow();  
        return null;      
    }
   
    class ReadKeyBoardThread implements Runnable {
        private Thread sessionThread;
        boolean readKeyboard = true;
        public ReadKeyBoardThread(Thread thread) {
            this.sessionThread = thread;
        }

        public void abort() {
            readKeyboard = false;            
        }
        
        public void run() {
            while (readKeyboard) {
                try {
                    int c = session.getKeyboard().read();
                    if (c < 0) {
                        sessionThread.interrupt();
                        break;
                    }
                } catch (IOException e) {
                    break;
                }
                
            }
        }
    } 
    
    class PrintEventThread implements Runnable {

        PrintStream out = System.out;
        boolean doDisplay = true;

        public void run() {
            int minLevel = Integer.MAX_VALUE;
            if (level != null) {
                String lvl = level.toLowerCase();
                if ("debug".equals(lvl)) {
                    minLevel = DEBUG_INT;
                } else if ("info".equals(lvl)) {
                    minLevel = INFO_INT;
                } else if ("warn".equals(lvl)) {
                    minLevel = WARN_INT;
                } else if ("error".equals(lvl)) {
                    minLevel = ERROR_INT;
                }
            }
            Iterable<PaxLoggingEvent> le = logService.getEvents(entries == 0 ? Integer.MAX_VALUE : entries);
            for (PaxLoggingEvent event : le) {
                if (event != null) {
                    int sl = event.getLevel().getSyslogEquivalent();
                    if (sl <= minLevel) {
                        printEvent(out, event);
                    }
                }
            }
            // Tail
            final BlockingQueue<PaxLoggingEvent> queue = new LinkedBlockingQueue<PaxLoggingEvent>();
            PaxAppender appender = new PaxAppender() {
                public void doAppend(PaxLoggingEvent event) {
                        queue.add(event);
                }
            };
            try {
                logService.addAppender(appender);
                
                while (doDisplay) {
                    PaxLoggingEvent event = queue.take();
                    if (event != null) {
                        int sl = event.getLevel().getSyslogEquivalent();
                        if (sl <= minLevel) {
                            printEvent(out, event);
                        }
                    }
                }
            } catch (InterruptedException e) {
                // Ignore
            } finally {
                logService.removeAppender(appender);
            }
            out.println();
            
        }

        public void abort() {
            doDisplay = false;
        }

    }

}
