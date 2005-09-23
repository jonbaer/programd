/*
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version. You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software Foundation, Inc.,
 * 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 */

package org.aitools.programd.graph;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.aitools.programd.CoreSettings;
import org.aitools.programd.bot.Bot;
import org.aitools.programd.processor.aiml.RandomProcessor;
import org.aitools.programd.util.DeveloperError;
import org.aitools.programd.util.NoMatchException;
import org.aitools.programd.util.StringKit;
import org.aitools.programd.util.XMLKit;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * <p>
 * The <code>Graphmaster</code> is the &quot;brain&quot; of a Program D bot.
 * It consists of a collection of nodes called <code>Nodemapper</code>s.
 * These <code>Nodemapper</code> s map the branches from each node. The
 * branches are either single words or wildcards.
 * </p>
 * <p>
 * The root of the <code>Graphmaster</code> is a <code>Nodemapper</code>
 * with many branches, one for each of the first words of all the patterns. The
 * number of leaf nodes in the graph is equal to the number of categories, and
 * each leaf node contains the &lt;template&gt; tag.
 * </p>
 * 
 * @author Richard Wallace, Jon Baer
 * @author Thomas Ringate/Pedro Colla
 * @author <a href="mailto:noel@aitools.org">Noel Bush</a>
 * @author Eion Robb
 * @version 4.5
 */
public class Graphmaster
{
    // Public access convenience constants.

    /** A template marker. */
    public static final String TEMPLATE = "<template>";

    /** A that marker. */
    public static final String THAT = "<that>";

    /** A topic marker. */
    public static final String TOPIC = "<topic>";

    /** A botid marker. */
    public static final String BOTID = "<botid>";

    /** A filename marker. */
    public static final String FILENAME = "<filename>";

    /** The <code>*</code> wildcard. */
    public static final String ASTERISK = "*";

    /** The <code>_</code> wildcard. */
    public static final String UNDERSCORE = "_";

    /** A path separator. */
    public static final String PATH_SEPARATOR = ":";

    // Private convenience constants.

    /** An empty string. */
    private static final String EMPTY_STRING = "";

    /** The start of a marker. */
    private static final String MARKER_START = "<";

    /** A space. */
    private static final String SPACE = " ";

    /** Match states. */
    private static enum MatchState
    {
        /** Trying to match the input part of the path. */
        IN_INPUT,

        /** Trying to match the that part of the path. */
        IN_THAT,

        /** Trying to match the topic part of the path. */
        IN_TOPIC,

        /** Trying to match the botid part of the path. */
        IN_BOTID
    }

    // Instance variables.

    /** The logger. */
    private Logger logger = Logger.getLogger("programd");

    /** The root {@link Nodemaster} . */
    protected Nodemapper root = new Nodemaster();

    /** The merge policy. */
    private CoreSettings.MergePolicy mergePolicy;

    /** The separator string to use with the "append" merge policy. */
    private String mergeAppendSeparator;

    /** Whether to note each merge. */
    private boolean noteEachMerge;

    /** The AIML namespace URI in use. */
    private String aimlNamespaceURI;

    /** How frequently to provide a category load count. */
    private int categoryLoadNotifyInterval;
    
    /** The total number of categories read. */
    private int totalCategories = 0;

    /** The total number of path-identical categories that have been encountered. */
    private int duplicateCategories = 0;

    /** The response timeout. */
    protected int responseTimeout;

    /**
     * Creates a new Graphmaster, reading settings from
     * the given CoreSettings object.
     * 
     * @param settings the CoreSettings object from which to read settings
     */
    public Graphmaster(CoreSettings settings)
    {
        this.mergePolicy = settings.getMergePolicy();
        this.mergeAppendSeparator = settings.getMergeAppendSeparatorString();
        this.noteEachMerge = settings.mergeNoteEach();
        this.responseTimeout = settings.getResponseTimeout();
        this.categoryLoadNotifyInterval = settings.getCategoryLoadNotifyInterval();
        this.aimlNamespaceURI = settings.getAimlSchemaNamespaceUri();
    }
    
    /**
     * Creates a new Graphmaster, using default
     * settings as necessary.
     */
    public Graphmaster()
    {
        this.mergePolicy = CoreSettings.MergePolicy.COMBINE;
        this.mergeAppendSeparator = " ";
        this.noteEachMerge = false;
        this.responseTimeout = 1000;
        this.categoryLoadNotifyInterval = 5000;
        this.aimlNamespaceURI = "http://alicebot.org/2001/AIML-1.0.1";
    }

    /**
     * Adds a new pattern-that-topic path to the <code>Graphmaster</code>
     * root.
     * 
     * @param pattern &lt;pattern/&gt; path component
     * @param that &lt;that/&gt; path component
     * @param topic &lt;topic/&gt; path component
     * @param botid
     * @return <code>Nodemapper</code> which is the result of adding the path.
     */
    public Nodemapper add(String pattern, String that, String topic, String botid)
    {
        ArrayList<String> path = StringKit.wordSplit(pattern);
        path.add(THAT);
        path.addAll(StringKit.wordSplit(that));
        path.add(TOPIC);
        path.addAll(StringKit.wordSplit(topic));
        path.add(BOTID);
        path.add(botid);

        return add(path.listIterator(), this.root);
    }

    /**
     * Adds a new path to the <code>Graphmaster</code> at a given node.
     * 
     * @since 4.1.3
     * @param pathIterator an iterator over the List containing the elements of
     *            the path
     * @param parent the <code>Nodemapper</code> parent to which the child
     *            should be appended
     * @return <code>Nodemapper</code> which is the result of adding the node
     */
    private Nodemapper add(ListIterator<String> pathIterator, Nodemapper parent)
    {
        // If there are no more words in the path, return the parent node
        if (!pathIterator.hasNext())
        {
            parent.setTop();
            return parent;
        }
        // Otherwise, get the next word.
        String word = pathIterator.next();

        Nodemapper node;

        // If the parent contains this word, get the node with the word.
        if (parent.containsKey(word))
        {
            node = (Nodemapper) parent.get(word);
        }
        // Otherwise create a new node with this word.
        else
        {
            node = new Nodemaster();
            parent.put(word, node);
            node.setParent(parent);
        }

        // Return the result of adding the new node to the parent.
        return add(pathIterator, node);
    }

    /**
     * Removes a node, as well as many of its ancestors as have no descendants
     * other than this node or its ancestors.
     * 
     * @param nodemapper the mapper for the node to remove
     */
    private void remove(Nodemapper nodemapper)
    {
        Nodemapper parent = nodemapper.getParent();
        if (parent != null)
        {
            parent.remove(nodemapper);
            if (parent.size() == 0)
            {
                if (parent != this.root)
                {
                    remove(parent);
                }
            }
        }
        nodemapper = null;
    }

    /**
     * <p>
     * Searches for a match in the <code>Graphmaster</code> to a given path.
     * </p>
     * <p>
     * This is a high-level prototype, used for external access. It is not
     * synchronized!
     * </p>
     * 
     * @see #match(Nodemapper, Nodemapper, List, String, StringBuffer,
     *      MatchState, long)
     * @param input &lt;input/&gt; path component
     * @param that &lt;that/&gt; path component
     * @param topic &lt;topic/&gt; path component
     * @param botid &lt;botid/&gt; path component
     * @return the resulting <code>Match</code> object
     * @throws NoMatchException if no match was found
     */
    public Match match(String input, String that, String topic, String botid)
            throws NoMatchException
    {
        // Compose the input path. Fill in asterisks for empty values.
        ArrayList<String> inputPath;

        // Input text part.
        if (input.length() > 0)
        {
            inputPath = StringKit.wordSplit(input);
        }
        else
        {
            inputPath = new ArrayList<String>();
            inputPath.add(ASTERISK);
        }

        // <that> marker.
        inputPath.add(THAT);

        // Input <that> part.
        if (that.length() > 0)
        {
            inputPath.addAll(StringKit.wordSplit(that));
        }
        else
        {
            inputPath.add(ASTERISK);
        }

        // <topic> marker.
        inputPath.add(TOPIC);

        // Input <topic> part.
        if (topic.length() > 0)
        {
            inputPath.addAll(StringKit.wordSplit(topic));
        }
        else
        {
            inputPath.add(ASTERISK);
        }

        // <botid> marker.
        inputPath.add(BOTID);

        // Input [directed to] botid.
        inputPath.add(botid);

        // Get the match, starting at the root, with an empty star and path,
        // starting in "in input" mode.
        Match match = match(this.root, this.root, inputPath, EMPTY_STRING, new StringBuffer(),
                MatchState.IN_INPUT, System.currentTimeMillis() + this.responseTimeout);

        // Return it if not null; throw an exception if null.
        if (match != null)
        {
            return match;
        }
        // (otherwise...)
        throw new NoMatchException(input);
    }

    /**
     * <p>
     * Searches for a match in the <code>Graphmaster</code> to a given path.
     * </p>
     * <p>
     * This is a low-level prototype, used for internal recursion.
     * </p>
     * 
     * @see #match(String, String, String, String)
     * @param nodemapper the nodemapper where we start matching
     * @param parent the parent of the nodemapper where we start matching
     * @param input the input path (possibly a sublist of the original)
     * @param wildcardContent contents absorbed by a wildcard
     * @param path the path matched so far
     * @param matchState state variable tracking which part of the path we're in
     * @param expiration when this response process expires
     * @return the resulting <code>Match</code> object
     */
    private Match match(Nodemapper nodemapper, Nodemapper parent, List<String> input,
            String wildcardContent, StringBuffer path, MatchState matchState, long expiration)
    {
        // Return null if expiration has been reached.
        if (System.currentTimeMillis() >= expiration)
        {
            return null;
        }

        // Halt matching if this node is higher than the length of the input.
        if (input.size() < nodemapper.getHeight())
        {
            return null;
        }

        // The match object that will be returned.
        Match match;

        // If no more tokens in the input, see if this is a template.
        if (input.size() == 0)
        {
            // If so, the wildcard content is from the topic, and the path
            // component is the
            // topic.
            if (nodemapper.containsKey(TEMPLATE))
            {
                match = new Match();
                match.setBotID(path.toString());
                match.setNodemapper(nodemapper);
                return match;
            }
            // (otherwise...)
            return null;
        }

        // Take the first word of the input as the head.
        String head = input.get(0).trim();

        // Take the rest as the tail.
        List<String> tail = input.subList(1, input.size());

        /*
         * See if this nodemapper has a _ wildcard. _ comes first in the AIML
         * "alphabet".
         */
        if (nodemapper.containsKey(UNDERSCORE))
        {
            // If so, construct a new path from the current path plus a _
            // wildcard.
            StringBuffer newPath = new StringBuffer();
            synchronized (newPath)
            {
                if (path.length() > 0)
                {
                    newPath.append(path);
                    newPath.append(' ');
                }
                newPath.append('_');
            }

            // Try to get a match with the tail and this new path, using the
            // head as the wildcard content.
            match = match((Nodemapper) nodemapper.get(UNDERSCORE), nodemapper, tail, head, newPath,
                    matchState, expiration);

            // If that did result in a match,
            if (match != null)
            {
                // capture and push the wildcard content appropriate to the
                // current
                // match state.
                switch (matchState)
                {
                    case IN_INPUT:
                        if (wildcardContent.length() > 0)
                        {
                            match.pushInputWildcardContent(wildcardContent);
                        }
                        break;

                    case IN_THAT:
                        if (wildcardContent.length() > 0)
                        {
                            match.pushThatWildcardContent(wildcardContent);
                        }
                        break;

                    case IN_TOPIC:
                        if (wildcardContent.length() > 0)
                        {
                            match.pushTopicWildcardContent(wildcardContent);
                        }
                        break;
                        
                    case IN_BOTID:
                        assert false;
                        break;
                }
                // ...and return this match.
                return match;
            }
        }

        /*
         * The nodemapper may have contained a _, but this led to no match. Or
         * it didn't contain a _ at all.
         */
        if (nodemapper.containsKey(head))
        {
            /*
             * Check now whether this head is a marker for the <that>, <topic>
             * or <botid> segments of the path. If it is, set the match state
             * variable accordingly.
             */
            if (head.startsWith(MARKER_START))
            {
                if (head.equals(THAT))
                {
                    matchState = MatchState.IN_THAT;
                }
                else if (head.equals(TOPIC))
                {
                    matchState = MatchState.IN_TOPIC;
                }
                else if (head.equals(BOTID))
                {
                    matchState = MatchState.IN_BOTID;
                }

                // Now try to get a match using the tail and an empty star and
                // empty path.
                match = match((Nodemapper) nodemapper.get(head), nodemapper, tail, EMPTY_STRING,
                        new StringBuffer(), matchState, expiration);

                // If that did result in a match,
                if (match != null)
                {
                    // capture and push the star content appropriate to the
                    // *previous* match state.
                    switch (matchState)
                    {
                        case IN_THAT:
                            if (wildcardContent.length() > 0)
                            {
                                match.pushInputWildcardContent(wildcardContent);
                            }
                            // Remember the pattern segment of the matched path.
                            match.setPattern(path.toString());
                            break;

                        case IN_TOPIC:
                            if (wildcardContent.length() > 0)
                            {
                                match.pushThatWildcardContent(wildcardContent);
                            }
                            // Remember the that segment of the matched path.
                            match.setThat(path.toString());
                            break;

                        case IN_BOTID:
                            if (wildcardContent.length() > 0)
                            {
                                match.pushTopicWildcardContent(wildcardContent);
                            }
                            // Remember the topic segment of the matched path.
                            match.setTopic(path.toString());
                            break;

                        case IN_INPUT:
                            assert false;
                            break;
                    }
                    // ...and return this match.
                    return match;
                }
            }
            /*
             * In the case that the nodemapper contained the head, but the head
             * was not a marker, it must be that the head is a regular word. So
             * try to match the rest of the path.
             */
            else
            {
                // Construct a new path from the current path plus the head.
                StringBuffer newPath = new StringBuffer();
                synchronized (newPath)
                {
                    if (path.length() > 0)
                    {
                        newPath.append(path);
                        newPath.append(' ');
                    }
                    newPath.append(head);
                }

                // Try to get a match with the tail and this path, using the
                // current star.
                match = match((Nodemapper) nodemapper.get(head), nodemapper, tail, wildcardContent,
                        newPath, matchState, expiration);

                // If that did result in a match, just return it.
                if (match != null)
                {
                    return match;
                }
            }
        }

        /*
         * The nodemapper may have contained the head, but this led to no match.
         * Or it didn't contain the head at all. In any case, check to see if it
         * contains a * wildcard. * comes last in the AIML "alphabet".
         */
        if (nodemapper.containsKey(ASTERISK))
        {
            // If so, construct a new path from the current path plus a *
            // wildcard.
            StringBuffer newPath = new StringBuffer();
            synchronized (newPath)
            {
                if (path.length() > 0)
                {
                    newPath.append(path);
                    newPath.append(' ');
                }
                newPath.append('*');
            }

            // Try to get a match with the tail and this new path, using the
            // head as the star.
            match = match((Nodemapper) nodemapper.get(ASTERISK), nodemapper, tail, head, newPath,
                    matchState, expiration);

            // If that did result in a match,
            if (match != null)
            {
                // capture and push the star content appropriate to the current
                // match state.
                switch (matchState)
                {
                    case IN_INPUT:
                        if (wildcardContent.length() > 0)
                        {
                            match.pushInputWildcardContent(wildcardContent);
                        }
                        break;

                    case IN_THAT:
                        if (wildcardContent.length() > 0)
                        {
                            match.pushThatWildcardContent(wildcardContent);
                        }
                        break;

                    case IN_TOPIC:
                        if (wildcardContent.length() > 0)
                        {
                            match.pushTopicWildcardContent(wildcardContent);
                        }
                        break;
                        
                    case IN_BOTID:
                        assert false;
                        break;
                }
                // ...and return this match.
                return match;
            }
        }

        /*
         * The nodemapper has failed to match at all: it contains neither _, nor
         * the head, nor *. However, if it itself is a wildcard, then the match
         * continues to be valid and can proceed with the tail, the current
         * path, and the star content plus the head as the new star.
         */
        if (nodemapper.equals(parent.get(ASTERISK)) || nodemapper.equals(parent.get(UNDERSCORE)))
        {
            return match(nodemapper, parent, tail, wildcardContent + SPACE + head, path,
                    matchState, expiration);
        }

        /*
         * If we get here, we've hit a dead end; this null match will be passed
         * back up the recursive chain of matches, perhaps even hitting the
         * high-level match method (which will react by throwing a
         * NoMatchException), though this is assumed to be the rarest occurence.
         */
        return null;
    }

    /**
     * Adds a new category to the Graphmaster.
     * 
     * @param pattern the category's pattern
     * @param that the category's that
     * @param topic the category's topic
     * @param template the category's template
     * @param botid the bot id for whom to add the category
     * @param bot the bot for whom the category is being added
     * @param url the path from which the category comes
     */
    public void addCategory(String pattern, String that, String topic, String template,
            String botid, Bot bot, URL url)
    {
        // Make sure the path components are right.
        if (pattern == null)
        {
            pattern = ASTERISK;
        }
        if (that == null)
        {
            that = ASTERISK;
        }
        if (topic == null)
        {
            topic = ASTERISK;
        }

        if (this.totalCategories % this.categoryLoadNotifyInterval == 0 && this.totalCategories > 0)
        {
            this.logger.log(Level.INFO, this.totalCategories + " categories loaded so far.");
        }

        Nodemapper node = add(pattern, that, topic, botid);
        String storedTemplate = (String) node.get(TEMPLATE);
        if (storedTemplate == null)
        {
            node.put(FILENAME, url.toExternalForm());
            bot.addToPathMap(url, node);
            node.put(TEMPLATE, template);
            this.totalCategories++;
        }
        else
        {
            this.duplicateCategories++;
            switch (this.mergePolicy)
            {
                case SKIP:
                    if (this.noteEachMerge)
                    {
                        this.logger.log(Level.WARNING, "Skipping path-identical category from \""
                                + url + "\" which duplicates path of category from \""
                                + node.get(FILENAME) + "\": " + pattern + ":" + that + ":" + topic);
                    }
                    break;

                case OVERWRITE:
                    if (this.noteEachMerge)
                    {
                        this.logger.log(Level.WARNING,
                                "Overwriting path-identical category from \""
                                        + node.get(Graphmaster.FILENAME)
                                        + "\" with new category from \"" + url + "\".  Path: "
                                        + pattern + ":" + that + ":" + topic);
                    }
                    node.put(Graphmaster.FILENAME, url);
                    node.put(Graphmaster.TEMPLATE, template);
                    break;

                case APPEND:
                    if (this.noteEachMerge)
                    {
                        this.logger.log(Level.WARNING, "Appending template of category from \""
                                + url + "\" to template of path-identical category from \""
                                + node.get(Graphmaster.FILENAME) + "\": " + pattern + ":" + that
                                + ":" + topic);
                    }
                    node
                            .put(Graphmaster.FILENAME, node.get(Graphmaster.FILENAME) + ", "
                                    + url);
                    node.put(Graphmaster.TEMPLATE, appendTemplate(storedTemplate, template));
                    break;

                case COMBINE:
                    if (this.noteEachMerge)
                    {
                        this.logger.log(Level.WARNING, "Combining template of category from \""
                                + url + "\" with template of path-identical category from \""
                                + node.get(Graphmaster.FILENAME) + "\": " + pattern + ":" + that
                                + ":" + topic);
                    }
                    node
                            .put(Graphmaster.FILENAME, node.get(Graphmaster.FILENAME) + ", "
                                    + url);
                    String combined = combineTemplates(storedTemplate, template);
                    node.put(Graphmaster.TEMPLATE, combined);
                    break;
            }
        }
    }

    /**
     * Removes all nodes associated with a given filename, and removes the file
     * from the list of loaded files.
     * 
     * @param path the filename
     * @param bot the bot for whom to remove the given path
     */
    public void unload(Object path, Bot bot)
    {
        Set<Nodemapper> nodemappers = bot.getLoadedFilesMap().get(path);

        for (Nodemapper nodemapper : nodemappers)
        {
            remove(nodemapper);
            this.totalCategories--;
        }
    }

    /**
     * Combines two template content strings into a single template, using a
     * random element so that either original template content string has an
     * equal chance of being processed. The order in which the templates are
     * supplied is important: the first one (<code>existingTemplate</code>)
     * is processed as though it has already been stored in the Graphmaster, and
     * hence might itself be the result of a previous <code>combine()</code>
     * operation. If this is the case, the in-memory representation of the
     * template will have a special attribute indicating this fact, which will
     * be used to &quot;balance&quot; the combine operation.
     * 
     * @param existingTemplate the template with which the new template should
     *            be combined
     * @param newTemplate the template which should be combined with the
     *            existing template
     * @return the combined result
     */
    public String combineTemplates(String existingTemplate, String newTemplate)
    {
        Document existingDoc;
        Element existingRoot;
        NodeList existingContent;

        Document newDoc;
        NodeList newContent;

        try
        {
            existingDoc = XMLKit.parseAsDocumentFragment(existingTemplate);
            existingRoot = existingDoc.getDocumentElement();
            existingContent = existingRoot.getChildNodes();
    
            newDoc = XMLKit.parseAsDocumentFragment(newTemplate);
            newContent = newDoc.getDocumentElement().getChildNodes();
        }
        catch (DeveloperError e)
        {
            synchronized (this.logger)
            {
                this.logger.log(Level.WARNING, "Problem with existing or new template when performing merge combine.");
                this.logger.log(Level.WARNING, "existing template: " + existingTemplate);
                this.logger.log(Level.WARNING, "new template: " + newTemplate);
                this.logger.log(Level.WARNING, "Existing template will be retained as-is.");
            }
            return existingTemplate;
        }
            
        /*
         * If the existing template has a random element as its root, we need to
         * check whether this was the result of a previous combine.
         */
        Node firstNode = existingContent.item(0);
        if (firstNode instanceof Element)
        {
            Element firstElement = (Element) firstNode;
            if (firstElement.getNodeName().equals(RandomProcessor.label)
                    && firstElement.hasAttribute("synthetic"))
            {
                Element newListItem = existingDoc.createElementNS(this.aimlNamespaceURI,
                        RandomProcessor.LI);
                int newContentSize = newContent.getLength();
                for (int index = 0; index < newContentSize; index++)
                {
                    newListItem.appendChild(existingDoc.importNode(newContent.item(index), true));
                }
                firstElement.appendChild(newListItem);
            }
            return XMLKit.renderXML(existingDoc.getChildNodes(), false);
        }
        Element listItemForExisting = existingDoc.createElementNS(this.aimlNamespaceURI,
                RandomProcessor.LI);
        int existingContentSize = existingContent.getLength();
        for (int index = 0; index < existingContentSize; index++)
        {
            Node child = existingContent.item(index);
            if (child != null)
            {
                listItemForExisting.appendChild(child.cloneNode(true));
                existingRoot.removeChild(child);
            }
        }

        Element listItemForNew = newDoc.createElementNS(this.aimlNamespaceURI, RandomProcessor.LI);
        int newContentSize = newContent.getLength();
        for (int index = 0; index < newContentSize; index++)
        {
            listItemForNew.appendChild(newContent.item(index).cloneNode(true));
        }

        Element newRandom = existingDoc.createElementNS(this.aimlNamespaceURI,
                RandomProcessor.label);
        newRandom.setAttribute("synthetic", "yes");
        newRandom.appendChild(listItemForExisting);
        newRandom.appendChild(existingDoc.importNode(listItemForNew, true));

        existingRoot.appendChild(newRandom);

        return XMLKit.renderXML(existingDoc.getChildNodes(), false);
    }

    /**
     * Appends the contents of one template to another.
     * 
     * @param existingTemplate the template to which to append
     * @param newTemplate the template whose content should be appended
     * @return the combined result
     */
    public String appendTemplate(String existingTemplate, String newTemplate)
    {
        Document existingDoc;
        Element existingRoot;

        Document newDoc;
        NodeList newContent;

        try
        {
            existingDoc = XMLKit.parseAsDocumentFragment(existingTemplate);
            existingRoot = existingDoc.getDocumentElement();

            newDoc = XMLKit.parseAsDocumentFragment(newTemplate);
            newContent = newDoc.getDocumentElement().getChildNodes();
        }
        catch (DeveloperError e)
        {
            synchronized (this.logger)
            {
                this.logger.log(Level.WARNING, "Problem with existing or new template when performing merge append.");
                this.logger.log(Level.WARNING, "existing template: " + existingTemplate);
                this.logger.log(Level.WARNING, "new template: " + newTemplate);
                this.logger.log(Level.WARNING, "Existing template will be retained as-is.");
            }
            return existingTemplate;
        }

        // Append whatever text is configured to be inserted between the
        // templates.
        if (this.mergeAppendSeparator != null)
        {
            existingRoot.appendChild(existingDoc.createTextNode(this.mergeAppendSeparator));
        }

        int newContentLength = newContent.getLength();
        for (int index = 0; index < newContentLength; index++)
        {
            Node newNode = existingDoc.importNode(newContent.item(index), true);
            existingRoot.appendChild(newNode);
        }
        return XMLKit.renderXML(existingDoc.getChildNodes(), false);
    }

    /**
     * Returns the number of categories presently loaded.
     * 
     * @return the number of categories presently loaded
     */
    public int getTotalCategories()
    {
        return this.totalCategories;
    }

    /**
     * Returns the number of path-identical categories encountered.
     * 
     * @return the number of path-identical categories encountered
     */
    public int getDuplicateCategories()
    {
        return this.duplicateCategories;
    }
}