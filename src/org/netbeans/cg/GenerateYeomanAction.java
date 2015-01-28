package org.netbeans.cg;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.netbeans.api.extexecution.ExecutionDescriptor;
import org.netbeans.api.extexecution.ExecutionDescriptor.LineConvertorFactory;
import org.netbeans.api.extexecution.ExecutionService;
import org.netbeans.api.extexecution.ExternalProcessBuilder;
import org.netbeans.api.extexecution.input.InputProcessor;
import org.netbeans.api.extexecution.input.InputProcessors;
import org.netbeans.api.extexecution.input.LineProcessor;
import org.netbeans.api.extexecution.print.ConvertedLine;
import org.netbeans.api.extexecution.print.LineConvertor;
import org.netbeans.api.progress.ProgressHandle;
import org.netbeans.api.progress.ProgressHandleFactory;
import org.netbeans.api.progress.ProgressUtils;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.awt.ActionID;
import org.openide.awt.ActionReference;
import org.openide.awt.ActionRegistration;
import org.openide.awt.StatusDisplayer;
import org.openide.util.Exceptions;
import org.openide.util.NbBundle.Messages;

@ActionID(
        category = "Tools",
        id = "org.netbeans.cg.GenerateYoAction"
)
@ActionRegistration(
        displayName = "#CTL_GenerateYoAction",
        asynchronous = true
)
@ActionReference(path = "Menu/Generators", position = 10)
@Messages("CTL_GenerateYoAction=Generate Yeoman App")
public final class GenerateYeomanAction implements ActionListener {

    private static final String yo = "C:\\Users\\gwieleng\\AppData\\Roaming\\npm\\yo.cmd";
    private static final String yoProjectFolder = "C:\\test2";
    private static final String yoProjectCommand = "ko:app";
    private Process process;

    @Override
    public void actionPerformed(ActionEvent e) {
        ProgressUtils.showProgressDialogAndRun(new Runnable() {
            @Override
            public void run() {
                createYoApp();
            }
        }, "Create Yeoman application...");
    }

    private void createYoApp() {
        final ProgressHandle handle = ProgressHandleFactory.createHandle("Processing...");
        handle.start(100);
        try {
            String displayName = yoProjectCommand + " -- " + yoProjectFolder;
            final DialogLineProcessor dialogProcessor = new DialogLineProcessor();
            Callable<Process> callable = new Callable<Process>() {
                @Override
                public Process call() throws Exception {
                    process = new ExternalProcessBuilder(yo).
                            addArgument(yoProjectCommand).
                            workingDirectory(new File(yoProjectFolder)).call();
                    dialogProcessor.setWriter(new OutputStreamWriter(process.getOutputStream()));
                    return process;
                }
            };
            ExecutionDescriptor descriptor = new ExecutionDescriptor()
                    .frontWindow(true)
                    .inputVisible(true)
                    .postExecution(new Runnable() {
                        @Override
                        public void run() {
                            StatusDisplayer.getDefault().setStatusText("Created: " + yoProjectFolder);
                        }
                    })
                    .outConvertorFactory(new LineConvertorFactory() {
                        @Override
                        public LineConvertor newLineConvertor() {
                            return new Numbered();
                        }
                    })
                    .outProcessorFactory(new ExecutionDescriptor.InputProcessorFactory() {
                        @Override
                        public InputProcessor newInputProcessor(InputProcessor defaultProcessor) {
                            return InputProcessors.proxy(defaultProcessor, InputProcessors.bridge(new ProgressLineProcessor(process, handle, 100, 1)));
                        }
                    })
                    .errProcessorFactory(new ExecutionDescriptor.InputProcessorFactory() {
                        @Override
                        public InputProcessor newInputProcessor(InputProcessor defaultProcessor) {
                            return InputProcessors.proxy(defaultProcessor, InputProcessors.bridge(dialogProcessor));
                        }
                    })
                    ;
            ExecutionService service = ExecutionService.newService(callable, descriptor, displayName);
            Future<Integer> future = service.run();
            try {
                future.get();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException ex) {
                Exceptions.printStackTrace(ex.getCause());
            }
        } finally {
            handle.progress(100);
            handle.finish();
        }
    }

    private class Numbered implements LineConvertor {
        private int number;
        @Override
        public List<ConvertedLine> convert(String line) {
            List<ConvertedLine> result = Collections.singletonList(ConvertedLine.forText(number + ": " + line, null));
            number++;
            return result;
        }
    }
    
    private static class DialogLineProcessor implements LineProcessor {
        private Writer writer;
        @Override
        public void processLine(String line) {
            Writer answerWriter;
            synchronized (this) {
                answerWriter = writer;
            }
            if (answerWriter != null) {
                try {
                    answerWriter.write("y\n"); // NOI18N
                    answerWriter.flush();
                } catch (IOException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }
        }
        public void setWriter(Writer writer) {
            synchronized (this) {
                this.writer = writer;
            }
        }
        @Override
        public void close() {
            // noop
        }
        @Override
        public void reset() {
            // noop
        }
    }

}
