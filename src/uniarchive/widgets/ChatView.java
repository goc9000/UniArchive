/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.widgets;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Element;
import javax.swing.text.StyleConstants;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.StyleSheet;

import uniarchive.graphics.Smiley;
import uniarchive.graphics.SmileyManager;
import uniarchive.models.archive.IMService;
import uniarchive.models.archive.IMArchive.Account;
import uniarchive.models.archive.IMArchive.Contact;
import uniarchive.models.archive.IMArchive.Conversation;
import uniarchive.models.archive.IMArchive.Conversation.Reply;
import uniarchive.models.archive.IMArchive.Conversation.Speaker;

/**
 * Class for a control that displays a conversation.
 */
public class ChatView extends GridBagPanel
{
	private static final long serialVersionUID = 1L;
	
	public static final String ACTION_CONVERSATION_SET = "conversationSet";
	public static final String ACTION_CONVERSATION_LOADED = "conversationLoaded";
	public static final String ACTION_FIND_TERMS_CHANGED = "findTermsChanged";
	public static final String ACTION_FIND_MARK_CHANGED = "findMarkChanged";

	protected static final String COMMAND_FIND_NEXT = "findNext";
	protected static final String COMMAND_FIND_PREV = "findPrev";
	protected static final String COMMAND_CLEAR_FIND = "clearFind";
	
	protected static final Color[] SPEAKER_COLORS = new Color[] {
		new Color(0.50f, 0.50f, 0.50f), // reserved for system replies
		new Color(0.00f, 0.00f, 0.75f), // reserved for the local identity
		new Color(0.66f, 0.00f, 0.00f),
		new Color(0.00f, 0.50f, 0.00f),
		new Color(0.50f, 0.00f, 0.66f),
		new Color(0.66f, 0.33f, 0.00f),
		new Color(0.00f, 0.33f, 0.66f),
		new Color(0.66f, 0.00f, 0.33f)
	};
	
	protected JLabel _partiesLabel;
	protected JLabel _dateLabel;
	protected JLabel _serviceLabel;
	protected JPanel _headerPanel;
	protected JLabel _findLabel;
	protected JTextField _findField;
	protected JButton _nextButton;
	protected JButton _prevButton;
	protected JButton _clearButton;
	protected JEditorPane _repliesPane;
	protected JScrollPane _scrollPane;
	
	protected Conversation _conversation;
	protected List<Reply> _replies;
	protected Map<Speaker, Integer> _speakerColors;
	
	protected Pattern _findPattern;
	protected FindMark _findMark;
	
	protected List<ActionListener> _listeners = new ArrayList<ActionListener>();
	
	protected DisplayWorker _displayWorker;
	
	protected CommandButtonListener _cmdButtonListener = new CommandButtonListener();

	/**
	 * Constructor.
	 */
	public ChatView()
	{
		this._initUI();
		
		this._displayWorker = new DisplayWorker();
		this.setConversation(null);
	}
	
	/**
	 * Registers a listener for important events in this
	 * control, such as the loading of a conversation or
	 * the changing of the current find mark. These
	 * are modeled as ActionEvents with an appropriate
	 * string from the ACTION_ constants.
	 * 
	 * @param listener A listener object
	 */
	public void addListener(ActionListener listener)
	{
		this._listeners.add(listener);
	}
	
	/**
	 * Unregisters a listener for events in this control.
	 * 
	 * @param listener A listener object
	 */
	public void removeListener(ActionListener listener)
	{
		this._listeners.remove(listener);
	}
	
	/**
	 * Sets the conversation displayed by this control.
	 * 
	 * @param conv A conversation object. If this is null, the
	 *             current conversation is cleared.
	 */
	public void setConversation(Conversation conv)
	{
		this._displayWorker.stop();
		
		try
		{
			this._conversation = conv;
			this._replies = (conv != null) ? conv.getReplies() : null;
			this._speakerColors = (conv != null) ? this._colorizeSpeakers(conv) : null;
		}
		catch (Exception e)
		{
			this._conversation = null;
			this._replies = null;
			this._speakerColors = null;
		}
		
		this._findMark = null;
		this._displayWorker.start();
		
		this._updateConversationHeader();
		this._runButtonEnableLogic();
		this._fireEvent(ACTION_CONVERSATION_SET);
	}
	
	/**
	 * Gets the conversation displayed by this control.
	 * 
	 * @return A conversation object (or null if no conversation
	 *         is loaded).
	 */
	public Conversation getConversation()
	{
		return this._conversation;
	}
	
	/**
	 * Checks whether there is a conversation set in this
	 * control.
	 * 
	 * @return True if the control contains a conversation
	 */
	public boolean hasConversation()
	{
		return this._conversation != null;
	}
	
	/**
	 * Checks whether there is a find mark set in the
	 * conversation, i.e. a find operation is ongoing.
	 * 
	 * @return True if there is a find mark set
	 */
	public boolean hasFindMark()
	{
		return this._findMark != null;
	}
	
	/**
	 * Checks whether there is any text in the Find field.
	 * 
	 * @return True if the Find field contains some terms
	 */
	public boolean hasFindTerms()
	{
		return !this._findField.getText().isEmpty();
	}
	
	/**
	 * Gets the current find mark in this control.
	 * 
	 * @return A FindMark object, or null, if no mark is
	 *         present (i.e. find is not active)
	 */
	public FindMark getFindMark()
	{
		return this._findMark;
	}
	
	/**
	 * Executes the Find command.
	 */
	public void doFindFirst()
	{
		// Note: if the conversation hasn't loaded yet, nothing
		// happens; this isn't a problem, since a Find First command
		// will be automatically issued once the conversation loads anyway
		if ((this._conversation == null) || !this._displayWorker.isDone()) return;
		
		this._clearFindMark();

		this._findPattern = this._compileSearchString(this._findField.getText());
		if ((this._conversation != null) && (this._findPattern != null))
		{
			for (int index=0; index<this._replies.size(); index++)
			{
				Matcher mat = this._findPattern.matcher(this._replies.get(index).text);
				
				if (mat.find())
				{
					this._setFindMark(new FindMark(index, mat.start(), mat.end()));
					break;
				}
			}
		}
	}
	
	/**
	 * Executes the Find command with a given search string.
	 * 
	 * Note that this command can even be called when the
	 * current conversation has not finished loading (or there
	 * is no conversation at all).
	 * 
	 * @param searchString The terms to search for. This will
	 *                     replace the current contents of the
	 *                     Find field.
	 */
	public void doFindFirst(String searchString)
	{
		this._findField.setText(searchString);
	}
	
	/**
	 * Executes the Find Next command.
	 */
	public void doFindNext()
	{
		if (this._findMark == null) return;
		
		int currReply = this._findMark.replyIndex;
		int base = this._findMark.start+1;
		
		int sanity = this._replies.size()+1; // safety variable to avoid an infinte loop
		while (sanity>0)
		{
			Matcher mat = this._findPattern.matcher(this._replies.get(currReply).text);
			
			if (mat.find(base))
			{
				this._setFindMark(new FindMark(currReply, mat.start(), mat.end()));
				return;
			}
			
			currReply = (currReply+1)%(this._replies.size());
			base = 0;
			sanity--;
		}
	}
	
	/**
	 * Executes the Find Prev command.
	 */
	public void doFindPrev()
	{
		if (this._findMark == null) return;
		
		int currReply = this._findMark.replyIndex;
		int limit = this._findMark.start;
		
		int sanity = this._replies.size()+1; // safety variable to avoid an infinte loop
		while (sanity>0)
		{
			Matcher mat = this._findPattern.matcher(this._replies.get(currReply).text);
			
			int lastMatchStart = -1;
			int lastMatchEnd = -1;
			while (mat.find())
			{
				if ((limit != -1) && (mat.start() >= limit)) break;
				lastMatchStart = mat.start();
				lastMatchEnd = mat.end();
			}
			
			if (lastMatchStart != -1)
			{
				this._setFindMark(new FindMark(currReply, lastMatchStart, lastMatchEnd));
				return;
			}
			
			currReply = (currReply-1+this._replies.size())%(this._replies.size());
			limit = -1;
			sanity--;
		}
	}
	
	/**
	 * Executes the Clear Find command.
	 */
	public void doClearFind()
	{
		this._findField.setText("");
	}
	
	/**
	 * Reacts to a change in the contents of the
	 * Find field.
	 */
	protected void _onFindFieldChanged()
	{
		this.doFindFirst();
		
		this._runButtonEnableLogic();
		this._fireEvent(ACTION_FIND_TERMS_CHANGED);
	}
	
	/**
	 * Reacts to the finishing of the conversation
	 * loading process.
	 */
	protected void _onConversationLoaded()
	{
		this.doFindFirst();
		
		this._fireEvent(ACTION_CONVERSATION_LOADED);
	}
	
	/**
	 * Constructs a regular expression corresponding to
	 * the user-supplied terms in the search field.
	 * 
	 * @param searchString The user-supplied search string
	 * @return A regular expression, or null if the search
	 *         string is in some way invalid.
	 */
	protected Pattern _compileSearchString(String searchString)
	{		
		String[] tokens = searchString.trim().split("\\s+");
		if ((tokens.length == 0) || (tokens[0].isEmpty())) return null;
		
		StringBuilder buf = new StringBuilder();
		for (String token : tokens)
		{
			if (buf.length() > 0) buf.append("(?:\\s+|(?:\\S*\\s+\\S*)|.*)");
			buf.append(Pattern.quote(token));
		}
		
		return Pattern.compile(buf.toString(), Pattern.CASE_INSENSITIVE);
	}
	
	/**
	 * Sets the current find mark.
	 * 
	 * @param newMark The new find mark, as a FindMark object,
	 *                or null, to clear the mark
	 */
	protected void _setFindMark(FindMark newMark)
	{
		if (this._findMark != null) this._formatReplyWithMark(this._findMark.replyIndex, 0, 0); // Unmark previous reply
		this._findMark = newMark;
		if (newMark != null)
		{
			// Mark new reply and scroll to it
			this._formatReplyWithMark(newMark.replyIndex, newMark.start, newMark.end);
			this._scrollToReply(newMark.replyIndex);
		}
		
		this._runButtonEnableLogic();		
		this._fireEvent(ACTION_FIND_MARK_CHANGED);
	}
	
	/**
	 * Removes the current find mark.
	 */
	protected void _clearFindMark()
	{
		this._setFindMark(null);
	}
	
	/**
	 * Reformats a reply in this control so that it includes a
	 * find mark. The same function is also used to delete the
	 * mark (use markStart=markEnd).
	 * 
	 * @param replyIndex The index of the reply
	 * @param markStart The position in the reply at
	 *                  which the marking begins (inclusive)
	 * @param markEnd The position in the reply at
	 *                which the marking ends (exclusive)
	 */
	protected void _formatReplyWithMark(int replyIndex, int markStart, int markEnd)
	{
		HTMLDocument doc = this._getHtmlDoc();
		Element replyElem = doc.getElement("reply"+replyIndex);
		if (replyElem == null) return;
		
		Reply reply = this._replies.get(replyIndex);
		
		try
		{
			doc.setInnerHTML(replyElem,
					this._getReplyTimeTdHtml(reply)+
					this._getReplySpeakerTdHtml(reply)+
					this._getReplyTextTdHtml(reply, markStart, markEnd));
		}
		catch (Exception e)
		{	
		}
	}
	
	/**
	 * Brings a reply into view.
	 * 
	 * @param replyIndex The index of the reply to scroll to
	 */
	protected void _scrollToReply(int replyIndex)
	{
		HTMLDocument doc = this._getHtmlDoc();
		Element replyElem = doc.getElement("reply"+replyIndex);
		if (replyElem == null) return;
		
		int currPos = this._repliesPane.getCaretPosition();
		int startPos = replyElem.getStartOffset();
		int endPos = replyElem.getEndOffset()-1;
		
		this._repliesPane.setCaretPosition((currPos > endPos) ? startPos : endPos);
	}
	
	/**
	 * Enables or disables buttons in this control
	 * according to the current situation.
	 */
	protected void _runButtonEnableLogic()
	{
		boolean haveConv = (this._conversation != null);
		boolean haveMark = (this._findMark != null);
		
		this._findLabel.setEnabled(haveConv);
		this._findField.setEnabled(haveConv);
		this._nextButton.setEnabled(haveMark);
		this._prevButton.setEnabled(haveMark);
		this._clearButton.setEnabled(!this._findField.getText().isEmpty());
	}
	
	/**
	 * Updates the conversation header UI so as to reflect
	 * the currently loaded conversation.
	 */
	protected void _updateConversationHeader()
	{
		final SimpleDateFormat convDateFormat = new SimpleDateFormat("MMM d, yyyy HH:mm:ss");
		
		if (this._conversation == null)
		{
			this._scrollPane.setColumnHeader(null);
		}
		else
		{
			this._scrollPane.setColumnHeaderView(this._headerPanel);
			
			// Update Date field
			this._dateLabel.setText(convDateFormat.format(this._conversation.dateStarted));
			
			// Update Service field
			this._serviceLabel.setText(this._htmlEntities(this._conversation.localAccount.service.friendlyName));
			this._serviceLabel.setIconTextGap(6);
			this._serviceLabel.setIcon(this._conversation.localAccount.service.icon);
			
			// Update Parties field
			List<Contact> participants = this._conversation.getDistinctParticipants();
			
			int divSize = Math.max(64, (this._scrollPane.getSize().width-128));
			
			StringBuilder html = new StringBuilder();
			html.append("<html>");
			html.append("<div style=\"width: "+divSize+";\">");
			for (int i=0; i<participants.size(); i++)
			{
				Color color = SPEAKER_COLORS[Math.min(1+i, SPEAKER_COLORS.length-1)];
				
				if (i==1) html.append(" to ");
				if (i>1) html.append(", ");
				
				html.append("<span style=\"color: "+this._getColorCss(color)+"\"><b>");
				html.append(this._htmlEntities(participants.get(i).name));
				html.append("</b>");
				
				// Finds the account used by this participant
				Account account = (i==0) ? this._conversation.localAccount : this._conversation.remoteAccount;
				if (i>0)
					for (Speaker speaker : this._conversation.getSpeakers())
						if (participants.get(i).equals(speaker.account.getContact()))
						{
							account = speaker.account;
							break;
						}
				
				html.append(" ("+this._htmlEntities(account.name)+")");
				html.append("</span>");
			}
			html.append("</div>");
			html.append("</html>");
			
			this._partiesLabel.setText(html.toString());
		}
	}
	
	/**
	 * Notifies any registered listeners of an event
	 * in this control.
	 * 
	 * @param type The event type (an ACTION_* constant)
	 */
	protected void _fireEvent(String type)
	{
		ActionEvent event = new ActionEvent(this, ActionEvent.ACTION_PERFORMED, type);
		
		// Note: we will iterate over a copy of the listeners list,
		// as one possible response to the event is to unregister
		// listeners
		ActionListener[] listeners = this._listeners.toArray(new ActionListener[this._listeners.size()]);
		for (ActionListener listener : listeners) listener.actionPerformed(event);
	}
	
	/**
	 * Initializes the user interface in this control.
	 */
	protected void _initUI()
	{
		// Create main UI
		this._headerPanel = _createConversationHeaderPanel();
		this._repliesPane = this._createRepliesPane();
		
		this._scrollPane = new JScrollPane(this._repliesPane);
		this._scrollPane.setColumnHeaderView(this._headerPanel);
		
		this.setOpaque(false);
		this.add(this._scrollPane,                   0, 0, 1, 1, 1.0, 1.0, this.CENTER, this.BOTH ,  0, 0, 0, 0);
		this.add(this._createFindToolbar(),          0, 1, 1, 1, 1.0, 0.0, this.CENTER, this.BOTH ,  0, 4, 0, 0);
	}
	
	/**
	 * Creates the conversation header panel.
	 * 
	 * @return The newly created Conversation Header Panel control
	 */
	protected JPanel _createConversationHeaderPanel()
	{
		JLabel partiesLbl = new JLabel("Parties:");
		JLabel dateLbl = new JLabel("Date:");
		JLabel serviceLbl = new JLabel("Service:");
		
		final Color LABEL_COLOR = new Color(0.3f, 0.3f, 0.5f);
		partiesLbl.setForeground(LABEL_COLOR);
		dateLbl.setForeground(LABEL_COLOR);
		serviceLbl.setForeground(LABEL_COLOR);
		
		this._partiesLabel = new JLabel();
		this._dateLabel = new JLabel();
		this._serviceLabel = new JLabel();
		
		// Create header panel
		GridBagPanel hdrPanel = new GridBagPanel();
		hdrPanel.setOpaque(true);
		hdrPanel.setBackground(UIManager.getColor("TextPane.background"));
		hdrPanel.add(partiesLbl,           0, 0, 1, 1, 0.0, 0.0, this.EAST,   this.NONE ,  0, 0, 8, 8);
		hdrPanel.add(this._partiesLabel,   1, 0, 3, 1, 1.0, 0.0, this.WEST,   this.HORIZ,  0, 0, 0, 8);
		hdrPanel.add(dateLbl,              0, 1, 1, 1, 0.0, 0.0, this.EAST,   this.NONE ,  0, 0, 8, 0);
		hdrPanel.add(this._dateLabel,      1, 1, 1, 1, 0.5, 0.0, this.WEST,   this.HORIZ,  0, 0, 0, 0);
		hdrPanel.add(serviceLbl,           2, 1, 1, 1, 0.0, 0.0, this.EAST,   this.NONE , 16, 0, 8, 0);
		hdrPanel.add(this._serviceLabel,   3, 1, 1, 1, 0.5, 0.0, this.WEST,   this.HORIZ,  0, 0, 0, 0);
		
		hdrPanel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0.75f, 0.75f, 0.75f)),
				BorderFactory.createEmptyBorder(8,8,8,8)
				));
		
		this.addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent evt) {
				_updateConversationHeader();
			}
		});
		
		return hdrPanel;
	}
	
	/**
	 * Creates the main Replies Pane control and
	 * initializes it with an empty HTML document.
	 * 
	 * @return The newly created Replies Pane
	 */
	protected JEditorPane _createRepliesPane()
	{
		JEditorPane repliesPane = new JEditorPane();
		repliesPane.setEditable(false);
		repliesPane.setContentType("text/html");
		
		HTMLEditorKit htmlKit = new HTMLEditorKit();
		HTMLDocument doc = (HTMLDocument)htmlKit.createDefaultDocument();
		
		this._initStyleSheet(doc.getStyleSheet());
		repliesPane.setDocument(doc);
		
		return repliesPane;
	}
	
	/**
	 * Creates the Find toolbar that sits at the bottom of
	 * the control.
	 * 
	 * @return The newly created Find toolbar
	 */
	protected JComponent _createFindToolbar()
	{
		this._findLabel = new JLabel("Find");
		this._findField = new JTextField();
		this._nextButton = UIUtils.makeButton(null, "occurence+ovl_next", this._cmdButtonListener, COMMAND_FIND_NEXT);
		this._prevButton = UIUtils.makeButton(null, "occurence+ovl_prev", this._cmdButtonListener, COMMAND_FIND_PREV);
		this._clearButton = UIUtils.makeButton(null, "find+ovl_delete", this._cmdButtonListener, COMMAND_CLEAR_FIND);
	
		this._nextButton.setToolTipText("Find Next Occurence");
		this._prevButton.setToolTipText("Find Previous Occurence");
		this._clearButton.setToolTipText("Finish Search");
		
		GridBagPanel findBar = new GridBagPanel();
		findBar.setOpaque(false);
		
		findBar.add(this._findLabel,         0, 0, 1, 1, 0.0, 0.0, this.EAST,   this.NONE ,  8, 0, 8, 0);
		findBar.add(this._findField,         1, 0, 1, 1, 0.0, 0.0, this.CENTER, this.HORIZ,  0, 0, 8, 0);
		findBar.add(this._nextButton,        2, 0, 1, 1, 0.0, 0.0, this.CENTER, this.NONE,   0, 0, 0, 0);
		findBar.add(this._prevButton,        3, 0, 1, 1, 0.0, 0.0, this.CENTER, this.NONE,   0, 0, 0, 0);
		findBar.add(this._clearButton,       4, 0, 1, 1, 1.0, 0.0, this.WEST,   this.NONE,   8, 0, 0, 0);
		
		// KLUDGE: resize components
		int x = this._findField.getPreferredSize().height;
		this._findField.setPreferredSize(new Dimension(x*10, x));
		
		Insets margins = new Insets(2,2,2,2);
		this._nextButton.setMargin(margins);
		this._prevButton.setMargin(margins);
		this._clearButton.setMargin(margins);

		// Add listeners
		this._findField.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void changedUpdate(DocumentEvent ev) { _onFindFieldChanged(); }
			@Override
			public void insertUpdate(DocumentEvent ev) { _onFindFieldChanged(); }
			@Override
			public void removeUpdate(DocumentEvent ev) { _onFindFieldChanged(); }	
		});
		
		return findBar;
	}
		
	/**
	 * Initializes a CSS stylesheet with the rules needed for
	 * rendering replies.
	 * 
	 * @param styleSheet The stylesheet to initialize
	 */
	protected void _initStyleSheet(StyleSheet styleSheet)
	{
		styleSheet.addRule("body { background-color: "+this._getColorCss(UIManager.getColor("TextPane.background"))+";}");
		styleSheet.addRule("span.findmark { background-color: #f0ff00; color:inherit; }");
		styleSheet.addRule("td.time { width:4pc; color: #808080; }");
		styleSheet.addRule("td.systemreply { text-align:center; font-size:90%; color:"+this._getColorCss(SPEAKER_COLORS[0])+"; }");
		
		for (int i=1; i<SPEAKER_COLORS.length; i++)
		{
			String colorSpec = "color:"+this._getColorCss(SPEAKER_COLORS[i]);
			
			styleSheet.addRule("td.speaker"+i+" { font-weight: bold; width: 1%; padding-right: 0.3pc; "+colorSpec+"; }");
			styleSheet.addRule("td.reply"+i+" { "+colorSpec+"; }");
		}
	}
	
	/**
	 * Gets a reference to the HTML document that is used
	 * to display replies.
	 * 
	 * @return A HTMLDocument object
	 */
	protected HTMLDocument _getHtmlDoc()
	{
		return (HTMLDocument)this._repliesPane.getDocument();
	}
	
	/**
	 * Gets a reference to the BODY element in the HTML 
	 * document that is used to display replies.
	 * 
	 * @return A Element object
	 */
	protected Element _getDocumentBody()
	{
		HTMLDocument doc = this._getHtmlDoc();
	
		return doc.getElement(doc.getDefaultRootElement(), StyleConstants.NameAttribute, HTML.Tag.BODY);
	}
	
	/**
	 * Assigns a color index to each speaker, corresponding to
	 * its position of the corresponding participant in the
	 * distinct participants list.
	 * 
	 * @param conv A conversation object
	 * @return A map of speakers and their assigned colors
	 */
	protected Map<Speaker, Integer> _colorizeSpeakers(Conversation conv)
	{
		Map<Speaker, Integer> colors = new TreeMap<Speaker, Integer>();

		List<Contact> participants = conv.getDistinctParticipants();
		
		for (Speaker speaker : conv.getSpeakers())
			colors.put(speaker, 1+participants.indexOf(speaker.account.getContact()));
		
		return colors;
	}
	
	/**
	 * Gets the color index corresponding to a speaker.
	 * 
	 * @param speaker A speaker object (may be null)
	 * @return The corresponding color index
	 */
	protected int _getColorForSpeaker(Speaker speaker)
	{
		return (speaker != null) ? Math.min(this._speakerColors.get(speaker).intValue(), SPEAKER_COLORS.length-1) : 0;
	}
	
	/**
	 * Gets CSS code for a color.
	 * 
	 * @param color A color
	 * @return A string of the form "#rrggbb"
	 */
	protected String _getColorCss(Color color)
	{
		return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
	}
	
	/**
	 * Gets HTML code for a system note.
	 * 
	 * @param text The note contents
	 * @return HTML code for displaying the note
	 */
	protected String _getSystemNoteHtml(String text)
	{
		return
			"<table width=\"100%\" valign=\"baseline\">"+
				"<tr>"+
					"<td class=\"time\">&nbsp;</td>"+
					"<td class=\"systemreply\">"+
						this._htmlEntities(text)+
					"</td>"+
				"</tr>"+
			"</table>";
	}
	
	/**
	 * Gets HTML code for a reply.
	 * 
	 * @param reply An IM reply
	 * @param index The reply index in the conversation
	 * @return HTML code for displaying the reply
	 */
	protected String _getReplyHtml(Reply reply, int index)
	{
		return
			"<table width=\"100%\" valign=\"baseline\">"+
				"<tr id=\"reply"+index+"\">"+
					this._getReplyTimeTdHtml(reply)+
					this._getReplySpeakerTdHtml(reply)+
					this._getReplyTextTdHtml(reply)+
				"</tr>"+
			"</table>";
	}
	
	/**
	 * Gets HTML code for a reply's Time cell.
	 * 
	 * @param reply An IM reply
	 * @return HTML code for the respective TD
	 */
	protected String _getReplyTimeTdHtml(Reply reply)
	{
		final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm");
		
		return
			"<td class=\"time\">"+
				timeFmt.format(reply.date)+
			"</td>";
	}
	
	/**
	 * Gets HTML code for a reply's Speaker cell.
	 * 
	 * @param reply An IM reply
	 * @return HTML code for the respective TD
	 */
	protected String _getReplySpeakerTdHtml(Reply reply)
	{
		if (reply.speaker == null) return "";
		
		// Note: for some reason, the Swing HTML renderer considers the dot as a
		// line-breaking character, therefore we have to replace it with a similar-
		// looking Unicode character
		return
			"<td class=\"speaker"+this._getColorForSpeaker(reply.speaker)+"\">"+
				this._htmlEntities(reply.speaker.name+":").replaceAll(" ", "&nbsp;").replaceAll("[.]", "&#8228;")+
			"</td>";
	}
	
	/**
	 * Gets HTML code for a reply's Text cell.
	 * 
	 * @param reply An IM reply
	 * @return HTML code for the respective TD
	 */
	protected String _getReplyTextTdHtml(Reply reply)
	{
		return this._getReplyTextTdHtml(reply, 0, 0);
	}
	
	/**
	 * Gets HTML code for a reply's Text cell, while also
	 * including formatting for a mark in the reply text.
	 * 
	 * @param reply An IM reply
	 * @param markStart The starting position for a mark in the
	 *                  reply (used for highlighting Find occurences)
	 * @param markEnd The ending position for the mark in the reply.
	 *                The mark extends from markStart, inclusive, to
	 *                markEnd, exclusive, so markStart=markEnd indicates
	 *                an empty mark
	 * @return HTML code for the respective TD
	 */
	protected String _getReplyTextTdHtml(Reply reply, int markStart, int markEnd)
	{
		IMService service = (reply.speaker != null) ? reply.speaker.account.service : IMService.GENERIC;
		String htmlReplyText = this._getReplyTextHtml(reply.text, service, markStart, markEnd);
		
		return
			"<td class=\""+((reply.speaker != null) ? "reply"+this._getColorForSpeaker(reply.speaker) : "systemreply")+"\">"+
				htmlReplyText+
			"</td>";
	}		
	
	/**
	 * Formats the text of a reply for use in an HTML table,
	 * by performing the following:
	 * 
	 * - HTML character escaping
	 * - inserting invisible break points in long words
	 * - substituting line break characters with BR tags
	 * - substituting smiley sequences with images
	 * - inserting markup for highlighting a Find occurence
	 * 
	 * @param replyText The original reply text
	 * @param service The service used in the conversation
	 * @param markStart The starting position for a mark in the
	 *                  reply (used for highlighting Find occurences)
	 * @param markEnd The ending position for the mark in the reply.
	 *                The mark extends from markStart, inclusive, to
	 *                markEnd, exclusive, so markStart=markEnd indicates
	 *                an empty mark 
	 * @return The HTML representing the reply text
	 */
	protected String _getReplyTextHtml(String replyText, IMService service, int markStart, int markEnd)
	{
		final int MAX_WORD_LEN = 15;
		
		StringBuilder html = new StringBuilder();
		
		boolean markActive = false;
		int wordLength = 0; 
		
		Pattern patSmileys = SmileyManager.getInstance().getDetectionPattern(service);
		Matcher matcher = (patSmileys != null) ? patSmileys.matcher(replyText) : null;
		int nextSmileyPos = ((matcher != null) && matcher.find()) ? matcher.start() : -1;	
		
		for (int pos=0; pos<replyText.length(); )
		{
			char c = replyText.charAt(pos);
			
			// Handle Find occurence marking
			if ((pos >= markStart) && (pos < markEnd) && !markActive)
			{
				html.append("<span class=\"findmark\">");
				markActive = true;
			}
			if ((pos >= markEnd) && markActive)
			{
				html.append("</span>");
				markActive = false;
			}
			
			// Handle smiley substitution
			if (pos == nextSmileyPos)
			{
				html.append(this._getSmileyHtml(service, matcher.group()));
				pos += matcher.group().length();
				nextSmileyPos = matcher.find() ? matcher.start() : -1;
				wordLength = 0;
				continue;
			}
			
			// Handle word breaking
			boolean isWordBreak = (c == '\t') || (c == ' ') || (c == '\r') || (c == '\n');
			wordLength = isWordBreak ? 0 : wordLength+1;
			if (wordLength > MAX_WORD_LEN)
			{
				html.append("<span style=\"font-size:0;\"> </span>");
				wordLength = 1;
			}
			
			// Handle newline->BR transform
			if (c == '\n')
			{
				html.append("<br>");
				pos++;
				continue;
			}
			
			html.append(this._escapeHtmlChar(c));
			pos++;
		}
		if (markActive) html.append("</span>");
		
		return html.toString();
	}
	
	/**
	 * Formats a smiley sequence (by substituting the apropriate
	 * image, if smileys are enabled).
	 * 
	 * @param service The service used by the conversation
	 * @param smileyText The smiley sequence
	 * @return HTML for representing the smiley
	 */
	protected String _getSmileyHtml(IMService service, String smileyText)
	{
		Smiley smiley = SmileyManager.getInstance().getSmiley(service, smileyText);
		if (smiley == null) return this._htmlEntities(smileyText);
		
		double factor = getFont().getSize() * 1.4 / smiley.height;
		int width = (int)(smiley.width * factor);
		int height = (int)(smiley.height * factor);
		
		return " <img width=\""+width+
				"\" height=\""+height+
				"\" src=\""+smiley.url.toString()+
				"\" alt=\""+this._htmlEntities(smileyText)+
				"\"> ";
	}
	
	/**
	 * Escapes special HTML characters in a piece of text.
	 * The escaped text is suitable for use in both tag
	 * contents and attribute values.
	 * 
	 * @param text A text string
	 * @return The text string with HTML-relevant characters
	 *         escaped
	 */
	protected String _htmlEntities(String text)
	{
		StringBuilder html = new StringBuilder();
		
		for (int i=0; i<text.length(); i++)
			html.append(this._escapeHtmlChar(text.charAt(i)));
		
		return html.toString();
	}
	
	/**
	 * Computes a HTML character escape table of a given size.
	 * 
	 * @param tableSize 
	 * @return
	 */
	protected String[] _computeHtmlEscapeTable(int tableSize)
	{
		String[] table = new String[tableSize];
		
		for (int c=0; c<tableSize; c++)
			switch (c)
			{
			case ' ': case '\t': case '\r': case '\n':
				table[c] = ""+(char)c; break;
			case '"': table[c] = "&quot;"; break;
			case '<': table[c] = "&lt;"; break;
			case '>': table[c] = "&gt;"; break;
			case '&': table[c] = "&amp;"; break;
			case '\'': table[c] = "&#39;"; break;
			default:
				table[c] = ((c >= 32) && (c < 128)) ? ""+(char)c : "&#"+c+";"; break;
			}
		
		return table;
	}
	
	/**
	 * Escapes a single HTML character.
	 * 
	 * @param c A character
	 * @return A string containing the HTML equivalent
	 *         of that character.
	 */
	protected String _escapeHtmlChar(char c)
	{
		final String[] TABLE = this._computeHtmlEscapeTable(256);
		
		if (c == 8204) return ""; // KLUDGE: this Unicode character causes the JEditorPane to crash when used
		
		return (c<TABLE.length) ? TABLE[c] : "&#"+(int)c+";";
	}
	
	/**
	 * Structure for representing a find mark in this view.
	 */
	public static class FindMark
	{
		public int replyIndex;
		public int start;
		public int end;
		
		/**
		 * Constructor.
		 * 
		 * @param replyIndex The index of the marked reply
		 * @param start The character position at which the
		 *              marking begins (inclusive)
		 * @param end The character position at which the
		 *            marking ends (exclusive)
		 */
		public FindMark(int replyIndex, int start, int end)
		{
			this.replyIndex = replyIndex;
			this.start = start;
			this.end = end;
		}
	}
	
	/**
	 * Internal class that listens to click events on command
	 * buttons contained in this control and starts the corresponding
	 * actions.
	 */
	protected class CommandButtonListener implements ActionListener
	{
		@Override
		public void actionPerformed(ActionEvent evt)
		{
			if (evt.getSource() instanceof AbstractButton)
			{
				String commandId = evt.getActionCommand();
				
				if (commandId.equals(COMMAND_FIND_NEXT)) { doFindNext(); }
				else if (commandId.equals(COMMAND_FIND_PREV)) { doFindPrev(); }
				else if (commandId.equals(COMMAND_CLEAR_FIND)) { doClearFind(); }
			}
		}
	}
	
	/**
	 * This class is used to perform the potentially long-running work
	 * of loading and formatting replies in the document without
	 * blocking other tasks. It does so by keeping track of what work
	 * there is to be done, and using a timer to wake up periodically to
	 * perform a limited amount of work from the amount that remains,
	 * yielding control to other threads in between batches.
	 */
	protected class DisplayWorker
	{
		protected static final int TIMER_PERIOD_MS = 10;
		protected static final int BATCH_SIZE = 40;
		
		protected Timer _timer;
		
		protected boolean _active = false;
		protected int _repliesDisplayed;
		protected Date _prevDate;
		
		/**
		 * Constructor.
		 */
		public DisplayWorker()
		{
			this._timer = new Timer(TIMER_PERIOD_MS, new ActionListener()
			{
				@Override
				public void actionPerformed(ActionEvent e) { doWork(); }
			});
		}
		
		/**
		 * Starts a conversation display job.
		 * 
		 *  Note: the _replies and _speakerColors fields must
		 *  have been initialized prior to calling this function.
		 */
		public void start()
		{
			this._timer.stop();
			
			try
			{
				HTMLDocument doc = _getHtmlDoc();
				Element body = _getDocumentBody();
					
				// Handle empty conversation
				if (_conversation == null)
				{
					doc.setInnerHTML(body, "&nbsp;");
					this._done();
					return;
				}
				
				this._active = true;
				this._repliesDisplayed = 0;
				this._prevDate = _conversation.dateStarted;
				
				// Render conversation header and the first batch of replies
				doc.setInnerHTML(body, "<span style=\"font-size:0\">&nbsp;</span>");
				_repliesPane.setCaretPosition(0);
				
				this.doWork();
				
				this._timer.start();
			}
			catch (Exception e)
			{
				JOptionPane.showMessageDialog(getTopLevelAncestor(),
						"Error displaying replies:\n"+e.toString(),
						"Error", JOptionPane.ERROR_MESSAGE);
			}
		}
		
		/**
		 * Stops all ongoing work.
		 */
		public void stop()
		{
			this._timer.stop();
		}
		
		/**
		 * Checks whether this worker has completed its job.
		 * 
		 * @return True if the worker has finished displaying
		 *         the conversation
		 */
		public boolean isDone()
		{
			return !this._active;
		}
		
		/**
		 * Performs a bit of the remaining work.
		 */
		public void doWork()
		{
			final SimpleDateFormat longReplyDateFmt = new SimpleDateFormat("MMMM d, yyyy");
			
			if (!this._active)
			{
				this._timer.stop();
				return;
			}
			
			try
			{
				HTMLDocument doc = _getHtmlDoc();
				Element body = _getDocumentBody();
			
				int inBatch = 0;
				StringBuilder html = new StringBuilder();
					
				while ((this._repliesDisplayed < _replies.size()) && (inBatch<BATCH_SIZE))
				{
					Reply reply = _replies.get(this._repliesDisplayed);
					
					// Add signs between replies that are far apart in time
					// or occur on different dates
					if (!this._sameDay(reply.date, this._prevDate))
						html.append(_getSystemNoteHtml("- "+longReplyDateFmt.format(reply.date)+" -"));
					else if (reply.date.getTime() - this._prevDate.getTime() > 150000)
						html.append(_getSystemNoteHtml("- - -"));
					
					html.append(_getReplyHtml(reply, this._repliesDisplayed));

					this._prevDate = reply.date;
					inBatch++;
					this._repliesDisplayed++;
				}
				
				doc.insertBeforeEnd(body, html.toString());
					
				if (this._repliesDisplayed == _replies.size())
				{
					// Job complete
					this._done();
				}
			}
			catch (Exception e)
			{
				this._done();
				
				JOptionPane.showMessageDialog(getTopLevelAncestor(),
						"Error displaying replies:\n"+e.toString(),
						"Error", JOptionPane.ERROR_MESSAGE);
			}
		}
		
		/**
		 * This function is called when the worker has finished
		 * displaying the conversation. The main control will be
		 * notified of this fact.
		 */
		protected void _done()
		{
			this._active = false;
			this._timer.stop();
			
			_onConversationLoaded();
		}
				
		/**
		 * Checks whether two dates refer to the same day.
		 * 
		 * @param date1 A date
		 * @param date2 Another date
		 * @return True if the dates differ only in time, false otherwise.
		 */
		protected boolean _sameDay(Date date1, Date date2)
		{
			Calendar cal1 = Calendar.getInstance();
			Calendar cal2 = Calendar.getInstance();
			
			cal1.setTime(date1);
			cal2.setTime(date2);
			
			return ((cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR)) &&
				    (cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)));
		}
	}
}
