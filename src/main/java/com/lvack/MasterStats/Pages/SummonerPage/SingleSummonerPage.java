package com.lvack.MasterStats.Pages.SummonerPage;

import com.googlecode.wickedcharts.highcharts.options.*;
import com.googlecode.wickedcharts.highcharts.options.series.Point;
import com.googlecode.wickedcharts.highcharts.options.series.PointSeries;
import com.googlecode.wickedcharts.highcharts.options.series.SimpleSeries;
import com.googlecode.wickedcharts.wicket7.highcharts.Chart;
import com.lvack.MasterStats.Api.StaticData.RiotEndpoint;
import com.lvack.MasterStats.Db.DataClasses.ChampionMasteryItem;
import com.lvack.MasterStats.Db.DataClasses.ChampionStatisticItem;
import com.lvack.MasterStats.Db.DataClasses.SummonerItem;
import com.lvack.MasterStats.Db.DataClasses.SummonerStatisticItem;
import com.lvack.MasterStats.PageData.PageDataProvider;
import com.lvack.MasterStats.Pages.BasePage;
import com.lvack.MasterStats.Pages.ChampionPages.SingleChampionPage;
import com.lvack.MasterStats.Util.GradeComparator;
import com.lvack.MasterStats.Util.NumberFormatter;
import com.lvack.MasterStats.Util.Pair;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.wicket.RestartResponseAtInterceptPageException;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.image.ExternalImage;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.request.http.flow.AbortWithHttpErrorCodeException;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.wicketstuff.annotation.mount.MountPath;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * SingleSummonerPageClass for MasterStats
 *
 * @author Leon Vack
 */

/**
 * page showing in-depth statistics for a single summoner
 */
@Slf4j
@MountPath("/summoner")
public class SingleSummonerPage extends BasePage {

    public SingleSummonerPage(PageParameters parameters) {
        super(parameters, null);
        // get summoner key name and region from url (passed by summoner query forward page
        String regionName = parameters.get(0).toString("").toUpperCase();
        String summonerKeyName = parameters.get(1).toString("");

        // check if summoner key name and region are valid, return 404 if not
        if (summonerKeyName == null || summonerKeyName.length() == 0)
            throw new AbortWithHttpErrorCodeException(404, "The summoner name queried was invalid");
        if (!EnumUtils.isValidEnum(RiotEndpoint.class, regionName))
            throw new AbortWithHttpErrorCodeException(404, "The region queried was invalid");

        // get summoner statistic from cache (generated by query forward page)
        Pair<String, SummonerStatisticItem> summonerStatistic = PageDataProvider.summonerStatisticCache.stream()
                .filter(e -> summonerKeyName.equals(e.getKey()) && e.getValue().getSummonerItem()
                        .getSummonerKey().endsWith(regionName)).findFirst().orElse(null);
        // if statistic is not found, forward to query forward page to generate. this can occur if somebody uses
        // a direct link to a summoners statistic page without using the query function
        if (summonerStatistic == null)
            throw new RestartResponseAtInterceptPageException(SummonerQueryForwardPage.class, new PageParameters()
                    .add("summonerName", summonerKeyName).set("region", regionName));

        // load the summoner statistic and the summoner information into local variables
        SummonerStatisticItem statisticItem = summonerStatistic.getValue();
        SummonerItem summonerItem = statisticItem.getSummonerItem();

        // set page title, summoner name, the mastery score
        super.add(new Label("page_title", String.format("MasterStats - %s", summonerItem.getSummonerName())));
        add(new Label("summoner_name", summonerItem.getSummonerName()));
        add(new Label("summoner_score", String.format("Mastery Score: %s", NumberFormatter.formatLong(
                summonerItem.getMasteryScore()))));
        // set the total score to the sum of all individual scores
        add(new Label("total_score", String.format("Total Champion Points: %s", NumberFormatter.formatLong(
                statisticItem.getChampionMasteries().stream().mapToInt(ChampionMasteryItem::getChampionPoints).sum()))));

        // set summoners ranked label ("null" as tier is replaced with unranked)
        if ("null".endsWith(summonerItem.getTier())) add(new Label("summoner_rank", "Unranked"));
        else add(new Label("summoner_rank", String.format("%s %s",
                StringUtils.capitalize(summonerItem.getTier().toLowerCase()), summonerItem.getDivision())));
        add(new Label("summoner_level", String.format("Summoner Level: %d", summonerItem.getSummonerLevel())));

        // set summoner profile icon
        add(new ExternalImage("summoner_portrait", String.format(
                "http://ddragon.leagueoflegends.com/cdn/%s/img/profileicon/%d.png", PageDataProvider.version,
                summonerItem.getProfileIconId())));

        // get the 3 champions with the highest mastery score and add them to the top champions region
        List<ChampionMasteryItem> topChampions = statisticItem.getChampionMasteries().stream()
                .filter(s -> PageDataProvider.getChampionStatisticById(s.getChampionId()) != null)
                .sorted((s1, s2) -> s2.getChampionPoints() - s1.getChampionPoints()).limit(3)
                .collect(Collectors.toList());
        // if less then 3 champions were played, add null objects to fill missing places
        while (topChampions.size() < 3) topChampions.add(null);
        add(new TopChampionsView("top_champions", topChampions));

        // get the 6 champions with the highest mastery score and without a chest granted and
        // add them to the top chestless champions regions
        List<ChampionMasteryItem> topChestChampions = statisticItem.getChampionMasteries().stream()
                .filter(s -> PageDataProvider.getChampionStatisticById(s.getChampionId()) != null)
                .filter(s -> s.getChestGranted() == 0)
                .sorted((s1, s2) -> s2.getChampionPoints() - s1.getChampionPoints()).limit(6)
                .collect(Collectors.toList());
        // if less then 6 champions were played, add null objects to fill missing places
        while (topChestChampions.size() < 6) topChestChampions.add(null);
        List<List<ChampionMasteryItem>> topChestChampionPartitions = ListUtils.partition(topChestChampions, 3);
        // add the first 3 to the upper region
        add(new TopChampionsView("top_chest_champions_top", topChestChampionPartitions.get(0)));
        // and the second 3 to the lower region
        add(new TopChampionsView("top_chest_champions_bottom", topChestChampionPartitions.get(1)));

        // create level distribution chart
        Options levelOptions = new Options();
        levelOptions.setTooltip(new Tooltip());
        levelOptions.setTitle(new Title("Champion Levels"));

        // make chart a pie chart
        levelOptions.setChartOptions(new ChartOptions().setType(SeriesType.PIE));

        // disable chart shadows
        levelOptions.setPlotOptions(new PlotOptionsChoice().setPie(new PlotOptions().setShadow(false)));

        // create levels map and data series
        HashMap<Integer, Integer> levels = new HashMap<>();
        PointSeries levelSeries = new PointSeries();
        // initialize level map with 0 for each level
        IntStream.rangeClosed(ChampionStatisticItem.MIN_CHAMPION_LEVEL, ChampionStatisticItem.MAX_CHAMPION_LEVEL)
                .forEach(i -> levels.put(i, 0));
        // iterate over all champion masteries and increment matching level count
        statisticItem.getChampionMasteries().stream().map(ChampionMasteryItem::getChampionLevel)
                .forEach(l -> levels.put(l, levels.get(l) + 1));
        // add level counts to series and set name according to the level
        levels.entrySet().stream().map(e -> new Point(String.format("Level %d", e.getKey()), e.getValue()))
                .forEach(levelSeries::addPoint);
        // set series name and size
        levelSeries.setName("Champions");
        levelSeries.setSize(new PixelOrPercent(100, PixelOrPercent.Unit.PERCENT));
        // disable label shadows
        levelSeries.setDataLabels(new DataLabels().setShadow(false));

        // add series to chart
        levelOptions.addSeries(levelSeries);

        // add chart to site
        add(new Chart("level_chart", levelOptions));

        // create grades chart
        Options gradeOptions = new Options();
        // set tooltip formatter
        gradeOptions.setTooltip(new Tooltip().setFormatter(new Function()
                .setFunction("return this.x + this.series.name + ' ' + this.y")));
        gradeOptions.setTitle(new Title("Highest Champion Grades"));

        // make chart a column chart
        gradeOptions.setChartOptions(new ChartOptions().setType(SeriesType.COLUMN));

        // make chart a stacked column chart
        gradeOptions.setPlotOptions(new PlotOptionsChoice().setSeries(new PlotOptions().setStacking(Stacking.NORMAL)));

        // set categories on x-axis to the the base grades in correct order (S, A, B, C, D)
        gradeOptions.setxAxis(new Axis().setCategories(ChampionStatisticItem.GRADES.stream()
                .filter(g -> g.length() == 1).sorted(new GradeComparator()).collect(Collectors.toList())));
        // set minimum and title for the y-axis
        gradeOptions.setyAxis(new Axis().setMin(0).setTitle(new Title("Champion Count")));

        // add legend and reverse order of elements
        gradeOptions.setLegend(new Legend().setEnabled(false));

        // create grades map to store grade counts
        HashMap<String, HashMap<String, Integer>> grades = new HashMap<>();
        // add sub grades to grades map
        grades.put("+", new HashMap<>());
        grades.put(" ", new HashMap<>());
        grades.put("-", new HashMap<>());
        // initialize each base grade for each sub grade with a count of 0
        ChampionStatisticItem.GRADES.stream().filter(g -> g.length() == 1)
                .forEach(g -> grades.values().forEach(v -> v.put(g, 0)));

        // iterate over all grades
        statisticItem.getChampionMasteries().stream().map(ChampionMasteryItem::getHighestGrade)
                // remove all entries with no grade
                .filter(g -> !"null".equals(g)).forEach(g -> {
            // get base and sub grade
            String gradeChar = String.valueOf(g.charAt(0));
            String subGradeChar = g.length() == 1 ? " " : String.valueOf(g.charAt(1));

            // get map for current sub grade
            HashMap<String, Integer> grade = grades.get(subGradeChar);
            // increment current base grade count
            grade.put(gradeChar, grade.get(gradeChar) + 1);
        });

        // create a series for each sub grade containing the base grade counts and add all series to the chart
        grades.entrySet().stream()
                // order sub grades
                .sorted((e1, e2) -> GradeComparator.subGradeCompare(e1.getKey(), e2.getKey()))
                // create a new series for each sub grade with the sub grade as name
                .map(e -> new SimpleSeries().setName(e.getKey()).setData(e.getValue().entrySet().stream()
                        // order grade counts by base grade
                        .sorted((e1, e2) -> GradeComparator.staticCompare(e1.getKey(), e2.getKey()))
                        // get grade counts and use the as data for the series
                        .map(Map.Entry::getValue).collect(Collectors.toList())))
                // add all series
                .forEach(gradeOptions::addSeries);

        // add chart to site
        add(new Chart("grade_chart", gradeOptions));

        // create champions and chests chart
        Options chestOptions = new Options();
        chestOptions.setTooltip(new Tooltip());
        chestOptions.setTitle(new Title("Champions and Chests"));

        // make chart a pie chart

        chestOptions.setChartOptions(new ChartOptions().setType(SeriesType.PIE));

        // disable chart shadows
        chestOptions.setPlotOptions(new PlotOptionsChoice().setPie(new PlotOptions().setShadow(false)));

        // create variables for counts of chests granted, champions played and overall champions existing in the game
        long chestsGranted = statisticItem.getChampionMasteries().stream().filter(c -> c.getChestGranted() > 0).count();
        long championsPlayed = statisticItem.getChampionMasteries().size();
        long championsTotal = PageDataProvider.championIdKeyNameMap.size();

        // create data series
        PointSeries chestSeries = new PointSeries();
        // add points
        chestSeries.addPoint(new Point("Chests granted", chestsGranted));
        chestSeries.addPoint(new Point("Chest not granted", championsPlayed - chestsGranted));
        chestSeries.addPoint(new Point("Not played", championsTotal - championsPlayed));
        // set series name and size
        chestSeries.setName("Champions");
        chestSeries.setSize(new PixelOrPercent(100, PixelOrPercent.Unit.PERCENT));
        // disable label shadows
        chestSeries.setDataLabels(new DataLabels().setShadow(false));

        // add series to chart
        chestOptions.addSeries(chestSeries);

        // add chart to site
        add(new Chart("chest_chart", chestOptions));
    }

    /**
     * class to show the top champions using a list view
     */
    private class TopChampionsView extends ListView<ChampionMasteryItem> {
        TopChampionsView(String id, List<ChampionMasteryItem> list) {
            super(id, list);
        }

        @Override
        protected void populateItem(ListItem<ChampionMasteryItem> item) {
            ChampionMasteryItem mastery = item.getModelObject();
            ChampionStatisticItem championStatistic = null;
            // create boolean to storing whether or not element should be shown
            // set to false if mastery or championStatistic is null
            boolean visible = true;
            // check if mastery is null

            if (mastery != null) {
                championStatistic = PageDataProvider.getChampionStatisticById(mastery.getChampionId());
            } else {
                // if mastery is null create a new one and fill it with default data
                mastery = new ChampionMasteryItem();
                mastery.setChampionPoints(42);
                mastery.setChampionLevel(1);
                visible = false;
            }
            if (championStatistic == null) {
                // if champion statistic is null use default champion for statistic
                championStatistic = PageDataProvider.championStatisticMap.get("bard");
                visible = false;
            }

            // create link to champion page
            PageParameters linkParameters = new PageParameters();
            linkParameters.set(0, championStatistic.getKeyName());
            BookmarkablePageLink<String> link = new BookmarkablePageLink<>("champion_link",
                    SingleChampionPage.class, linkParameters);
            item.add(link);

            // add champion portrait, name and mastery score as well as champion level
            link.add(new ExternalImage("champion_portrait", championStatistic.getPortraitUrl()));
            link.add(new Label("champion_name", championStatistic.getChampionName()));
            link.add(new Label("champion_stats", String.format("%s - Level %d", NumberFormatter.formatLong(
                    mastery.getChampionPoints()), mastery.getChampionLevel())));

            // hide link if necessary
            link.setVisible(visible);
        }
    }
}
