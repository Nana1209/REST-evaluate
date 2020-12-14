package io.swagger.handler;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import edu.mit.jwi.Dictionary;
import edu.mit.jwi.IDictionary;
import edu.mit.jwi.item.*;


public class WordNet {
    private static IDictionary dict;
    private static URL url;
    private static String Path="D:\\wordNET/dict";
    public WordNet() throws IOException {
        //File wnDir=new File(Path);
        this.url=new URL("file", null, Path);
        this.dict=new Dictionary(url);
        dict.open();//打开词典
    }
    public static void main(String[] args) throws IOException{
        String Path="D:\\wordNET/dict";
        File wnDir=new File(Path);
        URL url=new URL("file", null, Path);
        IDictionary dict=new Dictionary(url);
        dict.open();//打开词典
        getSynonyms(dict); //testing
    }


    public static void getSynonyms(IDictionary dict){
        // look up first sense of the word "go"
        //IIndexWord idxWord2 = dict.
        IIndexWord idxWord =dict.getIndexWord("members", POS.NOUN);
        IWordID wordID = idxWord.getWordIDs().get(0) ; // 1st meaning
        IWord word = dict.getWord(wordID);
        ISynset synset = word.getSynset (); //ISynset是一个词的同义词集的接口


        // iterate over words associated with the synset
        for(IWord w : synset.getWords())
            System.out.println(w.getLemma());//打印同义词集中的每个同义词
        //找父类
        System.out.println("夫类");
        List<ISynsetID> hypernyms =synset.getRelatedSynsets(Pointer.HOLONYM_MEMBER);//通过指针类型来获取相关的词集，其中Pointer类型为HYPERNYM
        //List<ISynsetID> hypernyms =synset.getRelatedSynsets(Pointer.HYPERNYM);//通过指针类型来获取相关的词集，其中Pointer类型为HYPERNYM
        for(ISynsetID isID:hypernyms){
            List <IWord > words ;
            words = dict.getSynset(isID).getWords(); //从synset中获取一个Word的list
            for(IWord iw:words){
                String w=iw.getLemma();
                System.out.println(w);
            }
        }
        System.out.println("词的子类");
        List<IWordID> words=word.getRelatedWords(Pointer.HYPONYM);
        for(IWordID wid:words){
            System.out.println(dict.getWord(wid).getLemma());//词根
        }
    }

    /**
     * 检测是否具有上下文语义关系
     * @param splitPaths 通过“/”分割过的路径（不含属性）
     * @throws IOException
     */
    public static void hasRelation(List<String> splitPaths) throws IOException {
        /*String Path="D:\\wordNET/dict";
        //File wnDir=new File(Path);
        URL url=new URL("file", null, Path);
        IDictionary dict=new Dictionary(url);
        dict.open();//打开词典*/
        for(int i=1;i<splitPaths.size();i++){
            IIndexWord idxWord1 =dict.getIndexWord(splitPaths.get(i-1), POS.NOUN);
            IIndexWord idxWord2 =dict.getIndexWord(splitPaths.get(i), POS.NOUN);
            if(idxWord1==null || idxWord2==null || idxWord1.getWordIDs().isEmpty() || idxWord2.getWordIDs().isEmpty()){
                System.out.println(splitPaths.get(i)+" no at least two noun");
            }else{
                IWordID wordID1 = idxWord1.getWordIDs().get(0) ; // 1st meaning
                ISynset synset1=dict.getSynset(wordID1.getSynsetID());//获得第一个词所属词集
                IWordID wordID2 = idxWord1.getWordIDs().get(0) ; // 2nd meaning
                ISynset synset2=dict.getSynset(wordID2.getSynsetID());//获得第2个词所属词集
                List<ISynsetID> upperRelations=new ArrayList<>();
                upperRelations.addAll(synset2.getRelatedSynsets(Pointer.HYPERNYM));//more general,xxx is a kind of
                upperRelations.addAll(synset2.getRelatedSynsets(Pointer.HOLONYM_MEMBER));//xxx is a part of
                if(upperRelations.contains(synset1.getID())){
                    System.out.println(splitPaths.get(i-1)+" and "+splitPaths.get(i)+" has context semantic relationship!");
                }else{
                    System.out.println(splitPaths.get(i-1)+" and "+splitPaths.get(i)+" has no relationship");
                }
            }
        }
    }
}