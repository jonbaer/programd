/*    
    This program is free software; you can redistribute it and/or
    modify it under the terms of the GNU General Public License
    as published by the Free Software Foundation; either version 2
    of the License, or (at your option) any later version.
    
    You should have received a copy of the GNU General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, 
    USA.
*/

package org.aitools.programd.processor;

import org.aitools.programd.parser.TemplateParser;
import org.aitools.programd.parser.XMLNode;

/**
 *  Handles a
 *  <code><a href="http://aitools.org/aiml/TR/2001/WD-aiml/#section-thatstar">thatstar</a></code>
 *  element.
 *
 *  @version    4.1.3
 *  @author     Jon Baer
 *  @author     Thomas Ringate, Pedro Colla
 *  @author     Noel Bush
 */
public class ThatStarProcessor extends IndexedPredicateProcessor
{
    public static final String label = "thatstar";

    public String process(int level, XMLNode tag, TemplateParser parser)
        throws AIMLProcessorException
    {
        if (tag.XMLType == XMLNode.EMPTY)
        {
            return super.process(level, tag, parser, parser.getThatStars(), 1);
        }
        else
        {
            throw new AIMLProcessorException("<thatstar/> cannot have content!");
        }
    }
}
