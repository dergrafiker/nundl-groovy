package main

import groovy.transform.CompileStatic
import net.htmlparser.jericho.Element
import net.htmlparser.jericho.HTMLElementName
import net.htmlparser.jericho.Source
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.text.WordUtils
import org.apache.http.HttpHost
import org.apache.http.HttpResponse
import org.apache.http.client.HttpClient
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClients
import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import org.joda.time.format.DateTimeFormatter

import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.regex.Matcher
import java.util.regex.Pattern

@Grapes([
        @Grab(group = 'commons-io', module = 'commons-io', version = '2.4'),
        @Grab(group = 'joda-time', module = 'joda-time', version = '2.8.1'),
        @Grab(group = 'net.htmlparser.jericho', module = 'jericho-html', version = '3.3'),
        @Grab(group = 'org.apache.commons', module = 'commons-lang3', version = '3.4'),
        @Grab(group = 'org.apache.httpcomponents', module = 'httpclient', version = '4.5')
])
@CompileStatic
class nundl {
    private static DateTimeFormatter websiteDTF = DateTimeFormat.forPattern("dd.MM.yyyy HH.mm"); //23.10.2014 05.31
    private static DateTimeFormatter fileDTF = DateTimeFormat.forPattern("yyMMdd");
    private static Matcher parenthesisMatcher = Pattern.compile("\\([a-zA-z0-9\\.]+\\)").matcher("")
    private static Matcher nonAlphanumMatcher = Pattern.compile("[^a-zA-z0-9]+").matcher("")
    private static Matcher whitespaceMatcher = Pattern.compile("\\s+").matcher("")
    private
    static Matcher xmas2014Matcher = Pattern.compile(Pattern.quote("Weihnachten bei Noob und Nerd - ") + "\\d+" + Pattern.quote(" (25.12.2014)")).matcher("")
    private static HttpClient httpClient;
    public static final String[] GERMANCHARS = ["ä", "Ä", "ö", "Ö", "ü", "Ü", "ß"]
    public static final String[] GERMANCHARREPLACEMENTS = ["ae", "Ae", "oe", "Oe", "ue", "Ue", "ss"]


    public static void main(String[] args) {
        def startUri = new URI("http://www.einslive.de/einslive/comedy/noob-und-nerd/")
        def baseDir = new File(args[0])

        httpClient = initHttpClient()

        List<URI> playerLinks = collectPLayerLinks(startUri)

        playerLinks.each { URI playerLink ->
            Source subLink = new Source(playerLink.toURL());
            subLink.getAllElements("param").findAll {
                it.getAttributeValue("name").equalsIgnoreCase("flashvars")
            }.each { Element flashvars ->
                try {
                    def paramMap = flashvars.getAttributeValue("value").split("&").collect {
                        URLDecoder.decode(it as String, "UTF-8").split("=")
                    }.collectEntries {
                        [(it[0]): it[1]]
                    }

                    def trackerClipAirTime = paramMap.get("trackerClipAirTime") as String
                    def title = paramMap.get("trackerClipTitle") as String
                    URI dslSrc = new URI(paramMap.get("dslSrc") as String)
                    if (trackerClipAirTime != null) {
                        DateTime webDateTime = websiteDTF.parseDateTime(trackerClipAirTime).withMillisOfDay(0)
                        title = cleanTitle(title)
                        downloadFile(dslSrc, webDateTime, title, baseDir)
                    } else {
                        if (StringUtils.containsIgnoreCase(title, "Weihnachten bei Noob und Nerd") && title.contains("2014") ) {
                            handleCornerCaseXmas2014(dslSrc, title, baseDir)
                        } else {
                            println "could not find trackerClipAirTime in ${paramMap}"
                        }
                    }
                } catch (Exception e) {
                    println e.stackTrace
                }
            }
        }
    }

    private static void handleCornerCaseXmas2014(URI dslSrc, String title, File baseDir) {
        xmas2014Matcher.reset(title)
        if (xmas2014Matcher.matches()) {
            def number = StringUtils.remove(title, "Weihnachten bei Noob und Nerd - ")
            number = StringUtils.remove(number, " (25.12.2014)")
            number = StringUtils.leftPad(number, 2, '0')
            title = "weihnachten2014-teil" + number
            DateTime webDateTime = DateTime.parse("141225", fileDTF)
            downloadFile(dslSrc, webDateTime, title, baseDir)
        }
    }

    private static HttpClient initHttpClient() {
        final String host = System.getProperty("http.proxyHost");
        final String port = System.getProperty("http.proxyPort");
        HttpClient httpClient;
        if (host != null && port != null) {
            HttpHost proxy = new HttpHost(host, Integer.valueOf(port));
            httpClient = HttpClients.custom()
                    .setUserAgent("Mozilla/5.0 (Windows NT 6.3; rv:36.0) Gecko/20100101 Firefox/36.0")
                    .setProxy(proxy)
                    .build();
        } else {
            httpClient = HttpClients.custom()
                    .setUserAgent("Mozilla/5.0 (Windows NT 6.3; rv:36.0) Gecko/20100101 Firefox/36.0")
                    .build();
        }
        httpClient;
    }

    private static String cleanTitle(String inputTitle) {
        String cleanedTitle = inputTitle

        cleanedTitle = StringUtils.remove(cleanedTitle, "| 1LIVE Noob und Nerd")
        cleanedTitle = StringUtils.remove(cleanedTitle, "Noob und Nerd: ")
        cleanedTitle = StringUtils.remove(cleanedTitle, "Noob & Nerd:")
        cleanedTitle = StringUtils.remove(cleanedTitle, "Noob und Nerd")
        cleanedTitle = StringUtils.remove(cleanedTitle, " - ")
        cleanedTitle = removeAllOnMatch(parenthesisMatcher, cleanedTitle)

        cleanedTitle = WordUtils.capitalize(cleanedTitle)
        cleanedTitle = removeAllOnMatch(whitespaceMatcher, cleanedTitle)

        cleanedTitle = StringUtils.replaceEachRepeatedly(cleanedTitle, GERMANCHARS, GERMANCHARREPLACEMENTS)
        cleanedTitle = StringUtils.stripAccents(cleanedTitle)
        cleanedTitle = removeAllOnMatch(nonAlphanumMatcher, cleanedTitle)
        return cleanedTitle
    }

    private static String removeAllOnMatch(Matcher matcher, String input) {
        matcher.reset(input)
        if (matcher.find()) {
            input = matcher.replaceAll("")
        }
        input
    }

    private static void downloadFile(URI playerLink, DateTime webDateTime, String title, File baseDir) {
        String filename = fileDTF.print(webDateTime) + "_noob_und_nerd_" + title + ".mp3"
        File outFile = new File(baseDir, filename)

        if (outFile.exists()) {
            println "$outFile.name already exists skipping"
            return
        }
        println(String.format("%s -> %s", playerLink.toString(), outFile.getAbsolutePath()));
        try {
            HttpResponse response = httpClient.execute(new HttpGet(playerLink));
            Files.copy(response.getEntity().getContent(), Paths.get(outFile.toURI()), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static List<URI> collectPLayerLinks(URI startUri) {
        Source mainPageSource = new Source(startUri.toURL());
        def listOfStrings = mainPageSource.getAllElements(HTMLElementName.A).findAll {
            def content = it.getContent().toString().toLowerCase();
            content.contains("noob") && content.contains("nerd") && it.getAttributeValue("href").contains("player")
        }.collect {
            startUri.resolve(it.getAttributeValue("href"))
        }
        listOfStrings
    }
}
