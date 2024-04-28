package org.vgcpge.copilot.jdt;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.ListenerList;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.ui.text.java.ContentAssistInvocationContext;
import org.eclipse.jdt.ui.text.java.IJavaCompletionProposalComputer;
import org.eclipse.jdt.ui.text.java.JavaContentAssistInvocationContext;
import org.eclipse.jface.text.AbstractDocument;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.text.contentassist.ICompletionProposal;
import org.eclipse.jface.text.contentassist.IContextInformation;
import org.eclipse.lsp4e.ConnectDocumentToLanguageServerSetupParticipant;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.swt.custom.StyledText;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.Display;
import org.vgcpge.copilot.ls.LanguageServer;
import org.vgcpge.copilot.ls.TextDocumentPositionParams;
import org.vgcpge.copilot.ls.rpc.CompletionItem;
import org.vgcpge.copilot.ls.rpc.Completions;
import org.vgcpge.copilot.ls.rpc.CopilotLanguageServer;

public class CopilotCompletionProposalComputer implements IJavaCompletionProposalComputer {
	private static final ILog LOG = Platform.getLog(CopilotCompletionProposalComputer.class);

	public CopilotCompletionProposalComputer() {
	}

	@Override
	public void sessionStarted() {
	}

	@Override
	public List<ICompletionProposal> computeCompletionProposals(ContentAssistInvocationContext context,
			IProgressMonitor monitor) {
		List<ICompletionProposal> result = new ArrayList<>();
		try {
			CopilotLanguageServer ls = LanguageServer.getLastCreatedCopilopsLs();
			if (ls != null) {
				if (context instanceof JavaContentAssistInvocationContext c) {
					String path = c.getCompilationUnit().getUnderlyingResource().getLocation().toPortableString();
					String uri = "file:///"+ path;
					if (path.startsWith("/")) {
						uri = "file://"+ path;
					}
					var doc = context.getDocument();
					// ensure language server is connected to the document - hope that it is not registered multiple times
					new ConnectDocumentToLanguageServerSetupParticipant().setup(doc);
					
					var sel = context.getTextSelection();
					int line = sel.getStartLine();
					int lineOffset = doc.getLineOffset(line);
					int column = sel.getOffset() - lineOffset;
					var pos = new Position(line, column);
					var docPos = new TextDocumentPositionParams(uri, pos, 1);
					var params = new org.vgcpge.copilot.ls.CompletionParams(docPos);
					Completions completions = ls.getCompletionsCycling(params).join();
					if (completions != null && completions.completions != null) {
						completions.completions.forEach(completion -> {
							result.add(new Prop(completion, lineOffset, sel.getOffset()));
						});
					}
				}
			}
		} catch (Exception e) {
			LOG.error(e.getMessage(), e);
		}
		return result;
	}

	@Override
	public List<IContextInformation> computeContextInformation(ContentAssistInvocationContext context,
			IProgressMonitor monitor) {
		return null;
	}

	@Override
	public String getErrorMessage() {
		return null;
	}

	@Override
	public void sessionEnded() {
	}

	private static class Prop implements ICompletionProposal {

		private final CompletionItem completion;
		private int startLineOffset;
		private int currentOffset;

		public Prop(CompletionItem completion, int startLineOffset, int currentOffset) {
			this.completion = completion;
			this.startLineOffset = startLineOffset;
			this.currentOffset = currentOffset;
		}

		private static String rtrim(String s) {
			int i = s.length() - 1;
			while (i >= 0 && Character.isWhitespace(s.charAt(i))) {
				i--;
			}
			return s.substring(0, i + 1);
		}

		@Override
		public void apply(IDocument document) {
			try {
				if (Character.isWhitespace(completion.text.charAt(0))==false) {
					document.replace(currentOffset, 0, rtrim(completion.text));
				}else {
					document.replace(startLineOffset, currentOffset - startLineOffset, rtrim(completion.text));
				}
			} catch (BadLocationException e) {
				LOG.error(e.getMessage(), e);
			}
		}

		@Override
		public org.eclipse.swt.graphics.Point getSelection(IDocument document) {
			return null;
		}

		@Override
		public String getAdditionalProposalInfo() {
			String text = completion.text;
			String result =  text.replace("\t", "  ").replace("\n", "<br>");
			return result;
		}

		@Override
		public String getDisplayString() {
			String displayText = completion.displayText;
			return firstLine(displayText);
		}

		@Override
		public Image getImage() {
			return null;
		}

		@Override
		public IContextInformation getContextInformation() {
			return null;
		}

		private static String firstLine(String message) {
			message = message.strip();
			int position = message.indexOf('\n');
			if (position <= 0) {
				return message;
			}
			return message.substring(0, position);
		}
	}
}
