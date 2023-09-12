package ca.ualberta.autowise.scripts.slack.boltapp.validation

class SlackModalValidationError extends Exception{


    Map<String, String> errors = new HashMap<>()

    def merge(SlackModalValidationError other){
        this.errors.putAll(other.getErrors())
    }

    def addError(String srcBlockId, String msg){
        //Accumulate error messages for the same block id
        if (errors.containsKey(srcBlockId)){
            String fullText = errors.get(srcBlockId)
            fullText += "\n" + msg
            errors.put(srcBlockId, fullText)
        }else{
            errors.put(srcBlockId, msg)
        }
    }

    def getErrors(){
        return errors
    }


}
