package com.example;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.api.events.WidgetLoaded;

@PluginDescriptor(
    name = "GE History Margin",
    description = "Appends the calculated margin directly onto the Grand Exchange history lines",
    tags = {"grand exchange", "flipping", "margin", "history"},
    enabledByDefault = true
)
public class ExamplePlugin extends Plugin
{
    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    private static final Pattern COIN_PATTERN = Pattern.compile("([\\d,]+)\\s+coins");
    private static final int GE_HISTORY_GROUP_ID = 232;
    private static final int GE_HISTORY_CHILD_ID = 2; 

    @Subscribe
    public void onWidgetLoaded(WidgetLoaded event)
    {
        if (event.getGroupId() == GE_HISTORY_GROUP_ID)
        {
            // Safely schedules the logic to run on the client thread after widgets load
            clientThread.invokeLater(this::calculateAndAppendMargins);
        }
    }

    private void calculateAndAppendMargins()
    {
        Widget container = client.getWidget(GE_HISTORY_GROUP_ID, GE_HISTORY_CHILD_ID);
        if (container == null || container.getChildren() == null)
        {
            return;
        }

        Widget[] children = container.getChildren();
        Map<String, Integer> boughtPrices = new HashMap<>();
        Map<String, Integer> soldPrices = new HashMap<>();
        Map<String, Widget> soldWidgets = new HashMap<>();

        for (int i = 0; i < children.length; i++)
        {
            Widget w = children[i];
            if (w == null || w.getText() == null) continue;

            String text = w.getText();

            if (text.startsWith("Bought:") || text.startsWith("Sold:"))
            {
                boolean isSold = text.startsWith("Sold:");
                
                if (i + 1 < children.length && i + 3 < children.length)
                {
                    String itemName = children[i + 1].getText();
                    Widget priceWidget = children[i + 3];
                    
                    if (itemName != null && priceWidget != null && priceWidget.getText() != null)
                    {
                        int price = parseCoinAmount(priceWidget.getText());
                        if (price > 0)
                        {
                            if (isSold)
                            {
                                soldPrices.put(itemName, price);
                                soldWidgets.put(itemName, priceWidget);
                            }
                            else
                            {
                                boughtPrices.put(itemName, price);
                            }
                        }
                    }
                }
            }
        }

        for (String itemName : soldPrices.keySet())
        {
            if (boughtPrices.containsKey(itemName))
            {
                int soldPrice = soldPrices.get(itemName);
                int boughtPrice = boughtPrices.get(itemName);
                Widget targetWidget = soldWidgets.get(itemName);

                String currentText = targetWidget.getText();
                if (currentText.contains("Margin:")) continue;

                int tax = (int) Math.min(5000000, Math.floor(soldPrice * 0.02));
                int netMargin = soldPrice - boughtPrice - tax;

                String colorTag = (netMargin >= 0) ? "<col=00ff00>" : "<col=ff0000>";
                String finalString = currentText + " " + colorTag + "Margin: " + netMargin + "</col>";

                targetWidget.setText(finalString);
            }
        }
    }

    private int parseCoinAmount(String text)
    {
        Matcher matcher = COIN_PATTERN.matcher(text);
        if (matcher.find())
        {
            try
            {
                return Integer.parseInt(matcher.group(1).replace(",", ""));
            }
            catch (NumberFormatException e)
            {
                return -1;
            }
        }
        return -1;
    }
}
