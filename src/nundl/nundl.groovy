package nundl

import groovy.transform.CompileStatic
import net.htmlparser.jericho.HTMLElementName
import net.htmlparser.jericho.Source
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

@Grapes([
        @Grab(group = 'commons-io', module = 'commons-io', version = '2.4'),
        @Grab(group = 'joda-time', module = 'joda-time', version = '2.8.1'),
        @Grab(group = 'net.htmlparser.jericho', module = 'jericho-html', version = '3.3'),
        @Grab(group = 'org.apache.commons', module = 'commons-lang3', version = '3.4'),
        @Grab(group = 'org.apache.httpcomponents', module = 'httpclient', version = '4.5')
])
@CompileStatic
class nundl {
    private static DateTimeFormatter websiteDTF = DateTimeFormat.forPattern("yyyy-MM-dd"); //2015-07-21
    private static DateTimeFormatter fileDTF = DateTimeFormat.forPattern("yyMMdd");
    private static HttpClient httpClient;

    public static void main(String[] args) {
        def startUri = new URI("http://www1.wdr.de/radio/1live/comedy/noob-und-nerd/")
        def baseDir = new File(args[0])

        httpClient = initHttpClient()

        boolean pretendDownloading = false;

        List<URI> playerLinks = collectPLayerLinks(startUri)

        playerLinks.each { URI playerLink ->
            def fields = playerLink.path.split("/").last().replaceAll("einslive", "").replaceAll("1livenoobundnerd_", "").replaceAll("1livenoobundnerd\\d+_", "").replaceAll("\\.mp3", "").split("_")
            def date = fields[0]
            def filename = fields[1]

            DateTime webDateTime = websiteDTF.parseDateTime(date).withMillisOfDay(0)
            downloadFile(playerLink, webDateTime, filename, baseDir, pretendDownloading)
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

    private
    static void downloadFile(URI playerLink, DateTime webDateTime, String title, File baseDir, boolean pretendDownloading) {
        String filename = fileDTF.print(webDateTime) + "_noob_und_nerd_" + title + ".mp3"
        File outFile = new File(baseDir, filename)

        if (outFile.exists()) {
            println "$outFile.name already exists skipping"
            return
        }
        println(String.format("%s -> %s", playerLink.toString(), outFile.getAbsolutePath()));
        if (pretendDownloading) {
            println "download skipped - just pretending"
        } else {
            try {
                HttpResponse response = httpClient.execute(new HttpGet(playerLink));
                Files.copy(response.getEntity().getContent(), Paths.get(outFile.toURI()), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private static List<URI> collectPLayerLinks(URI startUri) {
        Source mainPageSource = new Source(startUri.toURL());
        def listOfStrings = mainPageSource.getAllElements(HTMLElementName.A).findAll {
            it.attributes.getValue("class")?.equalsIgnoreCase("button download")
        }.collect {
            startUri.resolve(it.getAttributeValue("href"))
        }
        listOfStrings
    }
}
