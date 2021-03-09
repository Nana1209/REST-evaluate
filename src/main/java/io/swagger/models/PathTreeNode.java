package io.swagger.models;

import java.util.Map;
import java.util.List;

public class PathTreeNode {
    String nodeName;
    Map<String,PathTreeNode> children;
    Map<String,String> parameters;
}
