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
        id = "org.netbeans.cg.GenerateAction"
)
@ActionRegistration(
        displayName = "#CTL_GenerateAction",
        asynchronous = true
)
@ActionReference(path = "Menu/Generators", position = 0)
@Messages("CTL_GenerateAction=Generate Grails App")
public final class GenerateGrailsAction implements ActionListener {

    private static final String grails = "C:\\grails\\grails-2.4.4\\bin\\grails.bat";
    private static final String grailsProjectFolder = "C:\\test1";
    private static final String grailsProjectName = "demo";
    private static final String grailsProjectCommand = "create-app";

    @Override
    public void actionPerformed(ActionEvent e) {
        ProgressUtils.showProgressDialogAndRun(new Runnable() {
            @Override
            public void run() {
                createGrailsApp();
            }
        }, "Create Grails application...");
    }

    private void createGrailsApp() {
        final ProgressHandle handle = ProgressHandleFactory.createHandle("Processing...");
        handle.start(100);
        try {
            final DialogLineProcessor dialogProcessor = new DialogLineProcessor();
            String displayName = grailsProjectCommand + " -- " + grailsProjectName;
            Callable<Process> callable = new Callable<Process>() {
                @Override
                public Process call() throws Exception {
                    Process process
                            = new ExternalProcessBuilder(grails).
                            addArgument(grailsProjectCommand).
                            addArgument(grailsProjectName).
                            addArgument("--non-interactive").
                            workingDirectory(new File(grailsProjectFolder)).call();
                    dialogProcessor.setWriter(new OutputStreamWriter(process.getOutputStream()));
                    return process;
                }
            };
             ExecutionDescriptor descriptor = new ExecutionDescriptor().
                    frontWindow(true).postExecution(new Runnable() {
                        @Override
                        public void run() {
                            StatusDisplayer.getDefault().setStatusText("Created: " + grailsProjectName);
                        }
                    });
            descriptor = descriptor.outProcessorFactory(new ExecutionDescriptor.InputProcessorFactory() {
                @Override
                public InputProcessor newInputProcessor(InputProcessor defaultProcessor) {
                    return InputProcessors.proxy(defaultProcessor, InputProcessors.bridge(new ProgressLineProcessor(handle, 100, 1)));
                }
            });
            descriptor = descriptor.errProcessorFactory(new ExecutionDescriptor.InputProcessorFactory() {
                @Override
                public InputProcessor newInputProcessor(InputProcessor defaultProcessor) {
                    return InputProcessors.proxy(defaultProcessor, InputProcessors.bridge(dialogProcessor));
                }
            });
            ExecutionService service = ExecutionService.newService(callable, descriptor, displayName);
            Future<Integer> future = service.run();
            try {
                Integer ret = future.get();
                if (ret != 0) {
                    String msg = "Error";
                    DialogDisplayer.getDefault().notify(
                            new NotifyDescriptor.Message(msg, NotifyDescriptor.WARNING_MESSAGE));
                }
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

    public class Numbered implements LineConvertor {

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
