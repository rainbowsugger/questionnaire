package com.baoshine.questionnaire.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
@AllArgsConstructor
@NoArgsConstructor
@Data
public class QuestionnaireDetailVO {

    private QuestionnaireVO questionnaireVO;
    /**
     * 问卷节点
     */
    private List<NodeVO> nodeVOS;

    /**
     * 节点路径
     */
    private List<PathVO> pathVOS;

}
