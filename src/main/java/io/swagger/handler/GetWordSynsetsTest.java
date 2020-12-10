package io.swagger.handler;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import edu.mit.jwi.Dictionary;
import edu.mit.jwi.IDictionary;
import edu.mit.jwi.item.IIndexWord;
import edu.mit.jwi.item.ISynset;
import edu.mit.jwi.item.IWord;
import edu.mit.jwi.item.IWordID;
import edu.mit.jwi.item.POS;


public class GetWordSynsetsTest {

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
        IIndexWord idxWord =dict.getIndexWord("go", POS.VERB);
        IWordID wordID = idxWord.getWordIDs().get(0) ; // 1st meaning
        IWord word = dict.getWord(wordID);
        ISynset synset = word.getSynset (); //ISynset是一个词的同义词集的接口


        // iterate over words associated with the synset
        for(IWord w : synset.getWords())
            System.out.println(w.getLemma());//打印同义词集中的每个同义词
    }
}