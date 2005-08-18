/*
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version. You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software Foundation, Inc.,
 * 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 */

package org.aitools.programd.processor.aiml;

import org.w3c.dom.Element;

import org.aitools.programd.Core;
import org.aitools.programd.parser.TemplateParser;

/**
 * Handles a
 * <code><a href="http://aitools.org/aiml/TR/2001/WD-aiml/#section-bot">bot</a></code>
 * element.
 * 
 * @see AIMLProcessor
 */
public class BotProcessor extends AIMLProcessor
{
    /**
     * Creates a new BotProcessor with the given Core.
     * 
     * @param coreToUse the Core to use in creating the BotProcessor
     */
    public BotProcessor(Core coreToUse)
    {
        super(coreToUse);
    }

    /** The label (as required by the registration scheme). */
    public static final String label = "bot";

    /**
     * Retrieves the value of the desired bot predicate.
     * 
     * @param element the <code>bot</code> element
     * @param parser the parser that is at work
     * @return the result of processing the element
     */
    @Override
    public String process(Element element, TemplateParser parser)
    {
        return parser.getCore().getBots().getBot(parser.getBotID()).getPropertyValue(element.getAttribute(NAME));
    }
}