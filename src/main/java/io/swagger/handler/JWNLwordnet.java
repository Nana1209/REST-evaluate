package io.swagger.handler;

import net.didion.jwnl.JWNL;
import net.didion.jwnl.JWNLException;
import net.didion.jwnl.data.*;
import net.didion.jwnl.data.list.*;
import net.didion.jwnl.dictionary.Dictionary;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class JWNLwordnet {
    public JWNLwordnet() throws FileNotFoundException, JWNLException {
        String propsFile = "D:\\安装包\\jwnl14-rc2\\jwnl14-rc2\\config\\file_properties.xml";
        JWNL.initialize(new FileInputStream(propsFile));
    }

    public static void main(String[] args) throws IOException, JWNLException {
        /*String propsFile = "D:\\安装包\\jwnl14-rc2\\jwnl14-rc2\\config\\file_properties.xml";
        JWNL.initialize(new FileInputStream(propsFile));
        IndexWord DOG = Dictionary.getInstance().getIndexWord(POS.NOUN, "dogs");
        IndexWord MEDIA = Dictionary.getInstance().getIndexWord(POS.NOUN, "medium");
        demonstrateListOperation(DOG);
        //demonstrateTreeOperation(MEDIA);
        List<Synset> synsets=getAllHyponymSynset(MEDIA);
        System.out.println(synsets.size());*/
        JWNLwordnet jwnLwordnet=new JWNLwordnet();
        List<String> test=new ArrayList<>();
        /*test.add("school");
        test.add("student");
        test.add("medium");
        test.add("newspaper");*/
        test.add("pet");
        test.add("dog");
        jwnLwordnet.hasRelation(test);
    }
    private static void demonstrateListOperation(IndexWord word) throws JWNLException {
 // Get all of the hypernyms (parents) of the first sense of <var>word</var>
        PointerTargetNodeList hypernyms = PointerUtils.getInstance().getDirectHypernyms(word.getSense(1));
        System.out.println("Direct hypernyms of \"" + word.getLemma() + "\":");
        for(int idx = 0; idx < hypernyms.size(); idx++){
            PointerTargetNode nn = (PointerTargetNode)hypernyms.get(idx);
            for(int wrdIdx = 0; wrdIdx < nn.getSynset().getWordsSize(); wrdIdx++){
                System.out.println("Syn" + idx + " of direct hypernyms of\"" + word.getLemma() + "\" : " +
                        nn.getSynset().getWord(wrdIdx).getLemma());
                }
            }
        hypernyms.print();
    }
    private static void demonstrateTreeOperation(IndexWord word) throws JWNLException {
              // Get all the hyponyms (children) of the first sense of <var>word</var>
        PointerTargetTree hyponyms = PointerUtils.getInstance().getHyponymTree(word.getSense(1));
        PointerTargetTreeNode rootnode = hyponyms.getRootNode();
        Synset rootsynset = rootnode.getSynset();
        System.out.println(rootsynset.getWord(0).getLemma());
        PointerTargetTreeNodeList childList = rootnode.getChildTreeList();
        PointerTargetTreeNode rootnode1=(PointerTargetTreeNode)childList.get(0);
        System.out.println(rootnode1.getSynset().getWord(0).getLemma());
        System.out.println("Hyponyms of \"" + word.getLemma() + "\":");
        hyponyms.print();
    }
    public static List<Synset> getAllHyponymSynset(IndexWord word) throws JWNLException {
        // Get all the hyponyms (children) of all senses of <var>word</var>
        List<Synset> result=new ArrayList<>();

        for(Synset wordSynset:word.getSenses()){
            PointerTargetTree hyponyms = PointerUtils.getInstance().getHyponymTree(wordSynset,4);
            PointerTargetTreeNode rootnode = hyponyms.getRootNode();
            LinkedList<PointerTargetTreeNode> stack = new LinkedList<>();
            if(rootnode==null){
                return result;
            }
            stack.add(rootnode);
            while(!stack.isEmpty()){
                PointerTargetTreeNode node = stack.pollLast();
                result.add(node.getSynset());
                PointerTargetTreeNodeList childList = node.getChildTreeList();
                if(childList!=null){
                    for(int i=0;i<childList.size();i++){
                        stack.add((PointerTargetTreeNode)childList.get(i));
                    }
                }

            }

        }

        return result;
    }
    public static List<Synset> getAllMeronymSynset(IndexWord word) throws JWNLException {
        // Get all the hyponyms (children) of all senses of <var>word</var>
        List<Synset> result=new ArrayList<>();

        for(Synset wordSynset:word.getSenses()){
            PointerTargetTree hyponyms = PointerUtils.getInstance().getInheritedMeronyms(wordSynset,4,4);
            PointerTargetTreeNode rootnode = hyponyms.getRootNode();
            LinkedList<PointerTargetTreeNode> stack = new LinkedList<>();
            if(rootnode==null){
                return result;
            }
            stack.add(rootnode);
            while(!stack.isEmpty()){
                PointerTargetTreeNode node = stack.pollLast();
                result.add(node.getSynset());
                PointerTargetTreeNodeList childList = node.getChildTreeList();
                if(childList!=null){
                    for(int i=0;i<childList.size();i++){
                        stack.add((PointerTargetTreeNode)childList.get(i));
                    }
                }

            }

        }

        return result;
    }
    public boolean hasCommonWord(Synset syn1,Synset syn2){
        for(Word word:syn1.getWords()){
            if(syn2.containsWord(word.getLemma())){
                return true;
            }
        }
        return false;
    }
    public boolean hasRelation(List<String> splitPaths) throws JWNLException {
        for(int i=1;i<splitPaths.size();i++){
            IndexWord idxWord1 =Dictionary.getInstance().getIndexWord(POS.NOUN, splitPaths.get(i-1));
            IndexWord idxWord2 =Dictionary.getInstance().getIndexWord(POS.NOUN, splitPaths.get(i));
            if(idxWord1==null || idxWord2==null || idxWord1.getSenses().length==0 || idxWord2.getSenses().length==0){
                System.out.println("no enough word");
            }else{
                List<Synset> word1Hypos=getAllHyponymSynset(idxWord1);//获得第一个词的所有下义词集
                word1Hypos.addAll(getAllMeronymSynset(idxWord1));//添加第一个词的所有成员词集
                Synset[] word2Synsets = idxWord2.getSenses();//获得第2个词所属词集
                for(Synset s1:word1Hypos){
                    for(Synset s2:word2Synsets){
                        if(hasCommonWord(s1,s2)){
                            System.out.println("! ! ! "+splitPaths.get(i-1)+" and "+splitPaths.get(i)+" has hyper relationship");
                            return true;
                        }
                    }
                }

            }
            System.out.println("no relationship");
        }

        return false;
    }
}
