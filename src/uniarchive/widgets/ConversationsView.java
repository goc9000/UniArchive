/*
 * (C) Copyright 2009-2011  Cristian Dinu <goc9000@gmail.com>
 * 
 * Licensed under the GPL-3.
 */

package uniarchive.widgets;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import javax.swing.AbstractButton;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import uniarchive.graphics.IconManager;
import uniarchive.models.ProgressEvent;
import uniarchive.models.ProgressListener;
import uniarchive.models.archive.ConversationsQuery;
import uniarchive.models.archive.IMArchive;
import uniarchive.models.archive.IMArchive.Conversation;

/**
 * Class for a conversations table that features a toolbar for
 * searching.
 */
public class ConversationsView extends GridBagPanel
{
	private static final long serialVersionUID = 1L;
	
	public static final String ACTION_SELECTION_CHANGED = "selectionChanged";
	public static final String ACTION_SEARCH_FINISHED = "searchFinished";
	
	protected static final String COMMAND_SEARCH = "search";
	protected static final String COMMAND_CANCEL_SEARCH = "cancelSearch";
	
	protected static final int MAX_SEARCH_RESULTS = 100;
	
	protected JTextField _searchField;
	protected JProgressBar _searchPgBar;
	protected JButton _searchButton;
	protected ConversationsTable _table;
	
	protected String _lastSearchString = "";
	protected boolean _searching = false;
	protected SearchWorker _searchWorker = null;
	
	protected List<ActionListener> _listeners = new ArrayList<ActionListener>();
	protected CommandButtonListener _cmdButtonListener = new CommandButtonListener();
	
	/**
	 * Constructor.
	 */
	public ConversationsView()
	{
		this._initUI();
	}
	
	/**
	 * Registers a listener for important events in this
	 * control, such as the changing of the selection or
	 * the completion of a search. These are modeled as
	 * ActionEvents with an appropriate string from the
	 * ACTION_ constants.
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
	 * Sets the archive viewed by this control.
	 * 
	 * @param archive The new archive object that is to
	 *                be viewed.
	 */
	public void setArchive(IMArchive archive)
	{
		this._cancelSearch();
		this._table.setArchive(archive);
		
		this._runButtonEnableLogic();
	}
	
	/**
	 * Gets the archive viewed by this control.
	 * 
	 * @return The currently viewed archive object.
	 */
	public IMArchive getArchive()
	{
		return this._table.getArchive();
	}
		
	/**
	 * Sets the query used by this control to fetch conversations.
	 * 
	 * @param query A conversations query
	 */
	public void setQuery(ConversationsQuery query)
	{
		this._cancelSearch();
		
		this._table.setQuery(query);
	}
	
	/**
	 * Gets the query used by this control to fetch conversations.
	 * 
	 * @return A conversations query
	 */
	public ConversationsQuery getQuery()
	{
		return this._table.getQuery();
	}
	
	/**
	 * Gets the current selection.
	 * 
	 * @return An array of selected conversations.
	 */
	public Conversation[] getSelection()
	{
		return this._table.getSelection();
	}
	
	/**
	 * Gets the first selected conversation.
	 * 
	 * @return A conversation object, or null if none is selected.
	 */
	public Conversation getSelectedConversation()
	{
		return this._table.getSelectedConversation();
	}
	
	/**
	 * Selects the first conversation in the control.
	 */
	public void selectFirst()
	{
		this._table.selectFirst();
	}
	
	/**
	 * Gets the last search string used in this control.
	 * 
	 * @return A search string.
	 */
	public String getLastSearchString()
	{
		return this._lastSearchString;
	}
	
	/**
	 * Executes the Search command.
	 */
	public void doSearch()
	{
		this.doSearch(this._searchField.getText());
	}
	
	/**
	 * Executes the Search command for a given set of terms.
	 * 
	 * @param searchString The terms to search for.
	 */
	public void doSearch(String searchString)
	{
		this._lastSearchString = searchString;
		
		Pattern pattern = this._compileSearchString(searchString);
		if (pattern == null) return;
		
		// Cancels the current search if one is already in progress
		this._cancelSearch();
		
		// Modify the controls to indicate that we've entered
		// search mode
		this._searching = true;
		this._runButtonEnableLogic();
		
		// Prepare and start up the search worker
		this._searchWorker = new SearchWorker(pattern);
		this._searchPgBar.setMaximum(1);
		this._searchPgBar.setValue(0);
		this._searchWorker.execute();
	}
	
	/**
	 * Executes the Cancel Search command.
	 */
	public void doCancelSearch()
	{
		this._cancelSearch();
	}
	
	/**
	 * Cancels a search if one is in progress.
	 */
	protected void _cancelSearch()
	{
		if (!this._searching) return;
		
		if (this._searchWorker != null) this._searchWorker.cancel(true);
		this._searching = false;
		this._runButtonEnableLogic();
	}
	
	/**
	 * Shows, hides, enables and disables controls
	 * according to the current state of the master
	 * control.
	 */
	protected void _runButtonEnableLogic()
	{
		boolean haveArchive = (this.getArchive() != null);
		boolean searching = this._searching;
		
		this._searchField.setVisible(!searching);
		this._searchPgBar.setVisible(searching);
		this._searchButton.setEnabled(haveArchive);
		
		this._searchButton.setText(searching ? "Cancel" : "Search");
		this._searchButton.setIcon(IconManager.getInstance().getIcon(searching ? "cancel" : "search"));
		this._searchButton.setActionCommand(searching ? COMMAND_CANCEL_SEARCH : COMMAND_SEARCH);
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
	 * Reacts to the user changing the selection in the table.
	 */
	protected void _onTableSelectionChanged()
	{
		this._fireEvent(ACTION_SELECTION_CHANGED);
	}
	
	/**
	 * Reacts to progress in the search job.
	 * 
	 * @param event An event object describing the progress
	 */
	protected void _onSearchProgress(ProgressEvent event)
	{
		if (event.totalItems == -1)
		{
			this._searchPgBar.setIndeterminate(true);
		}
		else
		{
			this._searchPgBar.setMaximum(event.totalItems);
			this._searchPgBar.setValue(event.completedItems);
			this._searchPgBar.setIndeterminate(false);
		}
	}
	
	/**
	 * Reacts to the completion of the current search job.
	 * 
	 * @param result If the search completed successfully,
	 *               a list of the conversations found;
	 *               otherwise, an Exception describing any
	 *               encountered error.
	 */
	@SuppressWarnings("unchecked")
	protected void _onSearchComplete(Object result)
	{
		this._searching = false;
		this._runButtonEnableLogic();
				
		if (result instanceof Exception)
		{
			JOptionPane.showMessageDialog(this.getTopLevelAncestor(), "Could not search archive:\n"+((Exception)result).getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
			return;
		}
		
		// Check result
		List<Conversation> results = (List<Conversation>)result;		
		if (results.isEmpty())
		{
			JOptionPane.showMessageDialog(this.getTopLevelAncestor(), "No conversations found.", "Note", JOptionPane.WARNING_MESSAGE);
			return;
		}
		
		// Display results
		ConversationsQuery query = this._table.getQuery().clone();
		query.filterConversations = results;
		this._table.setQuery(query);
		this._table.selectFirst();
		
		this._fireEvent(ACTION_SEARCH_FINISHED);
		
		if (results.size() >= MAX_SEARCH_RESULTS)
			JOptionPane.showMessageDialog(this, "Too many results found, showing only first "+results.size()+".", "Warning", JOptionPane.WARNING_MESSAGE);
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
		this.setOpaque(false);
		
		// Create main UI
		this._table = new ConversationsTable();
		
		JScrollPane scrollPane = new JScrollPane(this._table);
		// KLUDGE: this makes the table appear to extend all over the scrollpane
		scrollPane.getViewport().setBackground(this._table.getBackground());
		
		this.add(scrollPane                      , 0, 0, 1, 1, 1.0, 1.0, this.CENTER, this.BOTH ,  0, 0, 0, 0);
		this.add(this._createSearchToolbar()     , 0, 1, 1, 1, 1.0, 0.0, this.CENTER, this.BOTH ,  0, 4, 0, 0);
		
		this._table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
			@Override
			public void valueChanged(ListSelectionEvent ev) {
				if (!ev.getValueIsAdjusting()) _onTableSelectionChanged(); }
		});
	}
	
	/**
	 * Creates the Search toolbar that sits at the bottom of
	 * the control.
	 * 
	 * @return The newly created Search toolbar
	 */
	protected JComponent _createSearchToolbar()
	{
		this._searchField = new JTextField();
		this._searchPgBar = new JProgressBar();
		this._searchPgBar.setVisible(false);
		this._searchButton = UIUtils.makeButton("Search", "search", this._cmdButtonListener, COMMAND_SEARCH);
		
		GridBagPanel findBar = new GridBagPanel();
		findBar.setOpaque(false);
		findBar.add(this._searchField             , 0, 0, 1, 1, 1.0, 0.0, this.CENTER, this.HORIZ,  8, 0, 0, 0);
		findBar.add(this._searchPgBar             , 1, 0, 1, 1, 1.0, 0.0, this.CENTER, this.HORIZ,  8, 0, 0, 0);
		findBar.add(this._searchButton            , 2, 0, 1, 1, 0.0, 0.0, this.CENTER, this.NONE,   8, 0, 8, 0);
		
		return findBar;
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
				
				if (commandId.equals(COMMAND_SEARCH)) { doSearch(); }
				else if (commandId.equals(COMMAND_CANCEL_SEARCH)) { doCancelSearch(); }
			}
		}
	}
	
	/**
	 * Internal class for a Swing worker running the potentially
	 * long-duration task of searching conversations.
	 */
	protected class SearchWorker extends SwingWorker<Object,Object> implements ProgressListener
	{
		protected Object _result; 
		protected Pattern _searchPattern;
		
		/**
		 * Constructor.
		 * 
		 * @param searchPattern The pattern to search for in the replies
		 */
		public SearchWorker(Pattern searchPattern)
		{	
			this._searchPattern = searchPattern;
		}
		
		/**
		 * Executes the worker's actual job.
		 * 
		 * This procedure executes in the worker's private
		 * thread.
		 */
		@Override
		protected Object doInBackground() throws Exception
		{
			try
			{
				IMArchive archive = _table.getArchive();
				ConversationsQuery query = _table.getQuery().clone();
				query.filterConversations.clear();
				
				this._result = archive.searchConversations(query, this._searchPattern, MAX_SEARCH_RESULTS, this);
			}
			catch (Exception e)
			{
				this._result = e;
			}
			
			return this._result;
		}
		
		@Override
		/**
		 * Executes GUI operations at the end of this worker's task.
		 * 
		 * This procedure executes in the Swing event dispatch thread. 
		 */
		protected void done()
		{
			if (!this.isCancelled()) _onSearchComplete(this._result);
		}

		@Override
		/**
		 * Reacts to events published by the running job.
		 * 
		 * This procedure executes in the Swing event dispatch thread.
		 */
		protected void process(List<Object> events)
		{
			for (Object event : events)
			{
				if (event instanceof ProgressEvent)
				{
					_onSearchProgress((ProgressEvent)event);
				}
			}
		}
		
		@Override
		/**
		 * Reacts to progress in the search function.
		 * 
		 * This procedure executes in the worker's private
		 * thread.
		 */
		public void onProgress(ProgressEvent ev)
		{
			this.publish(ev);	
		}
	}
}
