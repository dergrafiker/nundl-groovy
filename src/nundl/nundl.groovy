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
        @Grab(group = 'commons-io', module = 'commons-io', version = '2.5'),
        @Grab(group = 'joda-time', module = 'joda-time', version = '2.9.4'),
        @Grab(group = 'net.htmlparser.jericho', module = 'jericho-html', version = '3.4'),
        @Grab(group = 'org.apache.commons', module = 'commons-lang3', version = '3.4'),
        @Grab(group = 'org.apache.httpcomponents', module = 'httpclient', version = '4.5.2')
])
@CompileStatic
class nundl {
    public static void main(String[] args) {
        DateTimeFormatter websiteDTF = DateTimeFormat.forPattern("yyyy-MM-dd"); //2015-07-21
        DateTimeFormatter fileDTF = DateTimeFormat.forPattern("yyMMdd");

        URI startUri = new URI("http://www1.wdr.de/radio/1live/comedy/noob-und-nerd/")
        boolean pretendDownloading = false;
        File baseDir = new File(args[0])

        HttpHost proxy = null
        final String host = System.getProperty("http.proxyHost");
        final String port = System.getProperty("http.proxyPort");
        if (host != null && port != null) {
            proxy = new HttpHost(host, Integer.valueOf(port));
        }

        String userAgent = "Mozilla/5.0 (Windows NT 6.1; WOW64; rv:40.0) Gecko/20100101 Firefox/40.1"
        HttpClient httpClient = HttpClients.custom().setUserAgent(userAgent).setProxy(proxy).build();

        Source mainPageSource = new Source(startUri.toURL());
        List<URI> playerLinks = mainPageSource.getAllElements(HTMLElementName.A).findAll {
            "button download".equalsIgnoreCase(it.attributes.getValue("class"))
        }.collect {
            startUri.resolve(it.getAttributeValue("href"))
        }

        playerLinks.each { URI playerLink ->
            String[] fields = playerLink.path.split("/").last().replaceAll("einslive", "").replaceAll("1livenoobundnerd_", "").replaceAll("1livenoobundnerd\\d+_", "").replaceAll("\\.mp3", "").split("_")
            String date = fields[0]
            String filename = fields[1]

            DateTime webDateTime = websiteDTF.parseDateTime(date).withMillisOfDay(0)
            String newFilename = fileDTF.print(webDateTime) + "_noob_und_nerd_" + filename + ".mp3"
            File outFile = new File(baseDir, newFilename)

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
    }
}
