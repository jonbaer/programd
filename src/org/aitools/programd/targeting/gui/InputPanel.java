/*
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version. You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software Foundation, Inc.,
 * 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 */

package org.aitools.programd.targeting.gui;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import org.aitools.programd.targeting.Target;

/**
 * A panel for viewing inputs that triggered targets.
 */
public class InputPanel extends TargetAwareTabulator
{
    /**
     * Creates a new <code>InputPanel</code>, loading data from the currently
     * live targets.
     */
    public InputPanel(TargetingGUI gui)
    {
        super(new String[]
            { "<input>", "<pattern>", "<that>", "<topic>" } , gui);
    } 

    public void updateFromTargets()
    {
        List targets = TargetingTool.getSortedTargets();
        Iterator targetIterator = targets.iterator();
        ArrayList rows = new ArrayList();

        while (targetIterator.hasNext())
        {
            Target target = (Target) targetIterator.next();

            String matchPattern = target.getMatchPattern();
            String matchThat = target.getMatchThat();
            String matchTopic = target.getMatchTopic();

            ListIterator inputIterator = target.getInputTexts().listIterator();

            ArrayList inputTexts = new ArrayList();

            while (inputIterator.hasNext())
            {
                String inputText = (String) inputIterator.next();

                // Do not list duplicate input texts.
                if (!inputTexts.contains(inputText))
                {
                    rows.add(new Object[]
                        { inputText, matchPattern, matchThat, matchTopic, target,
                                new Integer(inputIterator.previousIndex() + 1) } );
                    inputTexts.add(inputText);
                } 
            } 
        } 
        Object[][] newData = new Object[][] {} ;
    newData = (Object[][]) rows.toArray(newData);
    if (newData.length > 0)
    {
        reloadData(newData);
    } 
} 
}