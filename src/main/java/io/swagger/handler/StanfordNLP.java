package io.swagger.handler;
import edu.stanford.nlp.coref.data.CorefChain;
import edu.stanford.nlp.ling.*;
import edu.stanford.nlp.ie.util.*;
import edu.stanford.nlp.pipeline.*;
import edu.stanford.nlp.semgraph.*;
import edu.stanford.nlp.trees.*;
import edu.stanford.nlp.util.CoreMap;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StanfordNLP {
    public static String text = "getUserByID user/name. " +
            "In 2017, he went to Paris, France in the summer. " +
            "His flight left at 3:00pm on July 10th, 2017. " +
            "After eating some escargot for the first time, Joe said, \"That was delicious!\" " +
            "He sent a postcard to his sister Jane Smith. " +
            "After hearing about Joe's trip, Jane decided she might go to France one day.";
    static String texttest="getUserByName";
    /**
     * 词形还原
     * @param :字符串
     * @return List<String> 分词、提取词形还原后的结果
     * */
    public static List<String> getlemma(String text){
        //单词集合
        List<String> wordslist = new ArrayList<>();;
        //StanfordCoreNLP词形还原
        Properties props = new Properties();  // set up pipeline properties
        props.put("annotators", "tokenize, ssplit, pos, lemma");   //分词、分句、词性标注和词根信息。
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
        Annotation document = new Annotation(text);
        pipeline.annotate(document);
        List<CoreMap> words = document.get(CoreAnnotations.SentencesAnnotation.class);
        for(CoreMap word_temp: words) {
            for (CoreLabel token: word_temp.get(CoreAnnotations.TokensAnnotation.class)) {
                String lemma = token.get(CoreAnnotations.LemmaAnnotation.class);  // 获取对应上面word的词元信息，即我所需要的词形还原后的单词
                wordslist.add(lemma);
            }
        }
        return wordslist;
    }

    /**
     * 去除字符串中花括号以及里面内容
     * @param ppname
     * @return
     */
    public static String removeBrace(String ppname){
        if(ppname.contains("{")){
            Pattern pp = Pattern.compile("(\\{[^\\}]*\\})");
            Matcher m = pp.matcher(ppname);
            String pathclear = "";//去除属性{}之后的路径
            int endtemp=0;
            while(m.find()){
                pathclear+=ppname.substring(endtemp,m.start());
                endtemp=m.end();
            }
            pathclear+=ppname.substring(endtemp);
            ppname=pathclear;
        }
        return ppname;
    }

    public static String removeSlash(String ppname){
        Pattern pp = Pattern.compile("//+");
        Matcher m = pp.matcher(ppname);
        String pathclear = "";//去除属性{}之后的路径
        int endtemp=0;
        while(m.find()){
            pathclear+=ppname.substring(endtemp,m.start());
            pathclear+="/";
            endtemp=m.end();
        }
        pathclear+=ppname.substring(endtemp);
        if(pathclear.endsWith("/")){
            pathclear=pathclear.substring(0,pathclear.length()-1);
        }
        return pathclear;
    }

    public static void main(String[] args){
        // set up pipeline properties
        Properties props = new Properties();
        // set the list of annotators to run
        props.setProperty("annotators", "tokenize,ssplit,pos,lemma,ner,parse,depparse,coref,kbp,quote");
        // set a property for an annotator, in this case the coref annotator is being set to use the neural algorithm
        props.setProperty("coref.algorithm", "neural");
        // build pipeline
        StanfordCoreNLP pipeline = new StanfordCoreNLP(props);
        // create a document object
        CoreDocument document = new CoreDocument(text);
        CoreDocument document1 = new CoreDocument(texttest);
        // annnotate the document
        pipeline.annotate(document);
        pipeline.annotate(document1);
        // examples

        // 10th token of the document
        CoreLabel token = document1.tokens().get(0);//10th word-count
        System.out.println("Example: token");
        System.out.println(token);
        System.out.println();

        // text of the first sentence
        String sentenceText = document.sentences().get(0).text();
        System.out.println("Example: sentence");
        System.out.println(sentenceText);
        System.out.println();

        // second sentence
        CoreSentence sentence = document.sentences().get(0);

        // list of the part-of-speech tags for the second sentence
        List<String> posTags = sentence.posTags();
        System.out.println("Example: pos tags");
        System.out.println(posTags);
        System.out.println();

        // list of the ner tags for the second sentence
        List<String> nerTags = sentence.nerTags();
        System.out.println("Example: ner tags");
        System.out.println(nerTags);
        System.out.println();

        // constituency parse for the second sentence
        Tree constituencyParse = sentence.constituencyParse();
        System.out.println("Example: constituency parse");
        System.out.println(constituencyParse);
        System.out.println();

        // dependency parse for the second sentence
        SemanticGraph dependencyParse = sentence.dependencyParse();
        System.out.println("Example: dependency parse");
        System.out.println(dependencyParse);
        System.out.println();

        // kbp relations found in fifth sentence
        List<RelationTriple> relations =
                document.sentences().get(4).relations();
        System.out.println("Example: relation");
        System.out.println(relations.get(0));
        System.out.println();

        // entity mentions in the second sentence
        List<CoreEntityMention> entityMentions = sentence.entityMentions();
        System.out.println("Example: entity mentions");
        System.out.println(entityMentions);
        System.out.println();

        // coreference between entity mentions
        CoreEntityMention originalEntityMention = document.sentences().get(3).entityMentions().get(1);
        System.out.println("Example: original entity mention");
        System.out.println(originalEntityMention);
        System.out.println("Example: canonical entity mention");
        System.out.println(originalEntityMention.canonicalEntityMention().get());
        System.out.println();

        // get document wide coref info
        Map<Integer, CorefChain> corefChains = document.corefChains();
        System.out.println("Example: coref chains for document");
        System.out.println(corefChains);
        System.out.println();

        // get quotes in document
        List<CoreQuote> quotes = document.quotes();
        CoreQuote quote = quotes.get(0);
        System.out.println("Example: quote");
        System.out.println(quote);
        System.out.println();

        // original speaker of quote
        // note that quote.speaker() returns an Optional
        System.out.println("Example: original speaker of quote");
        System.out.println(quote.speaker().get());
        System.out.println();

        // canonical speaker of quote
        System.out.println("Example: canonical speaker of quote");
        System.out.println(quote.canonicalSpeaker().get());
        System.out.println();
    }
}
