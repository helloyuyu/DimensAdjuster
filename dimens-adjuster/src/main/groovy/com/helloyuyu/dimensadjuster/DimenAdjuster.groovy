package com.helloyuyu.dimensadjuster

import groovy.xml.MarkupBuilder
import java.util.regex.Pattern
import java.util.function.Predicate

class DimenAdjuster {
    int basicSW
    int[] adjustSWs
    String basicDimensXmlFilePath
    List<String> excludes
    boolean dimenValueHalfUp

    private DimenAdjuster(AdjustArgs args) {
        this.basicSW = args.basicSW
        this.adjustSWs = args.adjustSWs
        this.basicDimensXmlFilePath = args.basicDimensXmlFilePath
        this.excludes = args.excludes
        this.dimenValueHalfUp = args.dimenValueHalfUp
    }

    static DimenAdjuster create(AdjustArgs adjustArgs) {
        return new DimenAdjuster(adjustArgs)
    }

    /**
     *
     */
    void run() {
        if (null == basicDimensXmlFilePath) return
        if (null == adjustSWs || adjustSWs.length == 0) return
        List<Node> dpAndSpDimenNodes = parseDimensXmlFile(basicDimensXmlFilePath)
        if (dpAndSpDimenNodes == null || dpAndSpDimenNodes.isEmpty()) return
        adjustSWs.each { sw ->
            adjustDimens(sw, dpAndSpDimenNodes)
        }
    }
    /**
     * 解析 dimens.xml
     * @param filePath dimens.xml路径
     * @return
     */
    List<Node> parseDimensXmlFile(String filePath) {
        //根节点 <resource> </resource>
        Node resourcesNode = new XmlParser().parse(new File(filePath))
        //过滤dimen闭包
        def collectNodes = {
            Node resources, Predicate<Node> filter ->
                List<Node> dimenNodes = resources.dimen
                if (dimenNodes == null || dimenNodes.isEmpty()) return null
                dimenNodes.stream().filter(filter).toArray()
        }
        return collectNodes.call(resourcesNode, spAndDpDimensNodeFilter)
    }
    /**
     * 根据smallest-width计算调整的dimen数值，构建生成对应的xml文件
     * @param targetSW 要构建的smallest-width
     * @param dimensSource 要调整的dimens
     * @return
     */
    def adjustDimens(int targetSW, List<Node> dimensSource) {
        def stringWriter = new StringWriter()
        def targetWsXml = new MarkupBuilder(stringWriter)
        //构建<resources></resources>
        targetWsXml.resources {
            dimensSource.each { node ->
                String dimenValue = node.text()
                if (dimenValue.endsWith("dp")) {
                    //dp
                    float value = Float.parseFloat(dimenValue.substring(0, dimenValue.indexOf("dp")))
                    String resultStr = adjustDpValue.call(basicSW, targetSW, value) + "dp"
                    //构建<dimen name="...">...</dimen>
                    dimen(name: node.attribute("name"), resultStr)
                } else if (dimenValue.endsWith("sp")) {
                    //sp
                    float value = Float.parseFloat(dimenValue.substring(0, dimenValue.indexOf("sp")))
                    String resultStr = adjustSpValue.call(basicSW, targetSW, value) + "sp"
                    //构建<dimen name="...">...</dimen>
                    dimen(name: node.attribute("name"), resultStr)
                }
            }
        }
        FileOutputStream fileOutputStream = new FileOutputStream(createSWDimenXmlFile(targetSW))
        fileOutputStream.write(stringWriter.toString().getBytes())
        fileOutputStream.close()
    }


    def createSWDimenXmlFile(int targetSW) {
        File basicSwDimenXmlFile = new File(basicDimensXmlFilePath)
        File targetSwDimenXmlFile = new File(basicSwDimenXmlFile.getParentFile().getParentFile(), "/values-sw${targetSW}dp/dimens.xml")
        if (!targetSwDimenXmlFile.getParentFile().exists()) targetSwDimenXmlFile.getParentFile().mkdirs()
        if (!targetSwDimenXmlFile.exists()) targetSwDimenXmlFile.createNewFile()
        return targetSwDimenXmlFile
    }

    /**
     * 筛选需要调整的 dimen
     * 当前只匹配 dp sp 不处理px的值
     * 取一位精度
     *
     */
    def spAndDpDimensNodeFilter = new Predicate<Node>() {
        @Override
        boolean test(Node node) {
            String dimenValue = node.text()
            Pattern dimenRegex = Pattern.compile('[.0-9]*(dp|sp)')
            boolean preMatches = dimenRegex.matcher(dimenValue).matches()
            if (!preMatches) {
                return false
            }
            if (excludes == null || excludes.isEmpty()) {
                return true
            }
            return excludes.stream().filter(new Predicate<String>() {
                @Override
                boolean test(String s) {
                    return node.attribute("name") == s
                }
            }).toArray().size() == 0
        }
    }

    def adjustDpValue = {
        int basicSW, int targetSW, float value ->
            if (value < 1) return value
            float resultValue = targetSW * value / basicSW
            //((int) (resultValue * 10)) / 10
            return (int) (dimenValueHalfUp ? (resultValue + 0.5f) : resultValue)
    }
    def adjustSpValue = {
        int basicSW, int targetSW, float value ->
            if (value < 1) return value
            float resultValue = targetSW * value / basicSW
            return (int) (dimenValueHalfUp ? (resultValue + 0.5f) : resultValue)
    }
}



