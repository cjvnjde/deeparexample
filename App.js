import React, { useEffect, useRef, useState } from "react";
import {
  StyleSheet,
} from "react-native";

import DeepARView from "./DeepARView";


const App = () => {
  const deepARRef = useRef(null);
  const [isInitialized, setIsInitialized] = useState(false);

  const changeEffect = path => {
    if (isInitialized) {
      deepARRef?.current?.callNativeMethod('switchEffect', path);
    }
  };

  useEffect(() => {
    changeEffect('file:///android_asset/effects/lion');
  }, [isInitialized])

  return (
    <DeepARView
      ref={deepARRef}
      onEventSent={async event => {
        const {
          type,
          value,
          value2,
        } = event.nativeEvent;

        switch (type) {
          case 'initialized': {
            setIsInitialized(true);
            break;
          }
          default: {
            console.log(type);
          }
        }
      }}
      style={styles.deeparStyle}
    />
  );
};

const styles = StyleSheet.create({
  deeparStyle: {
    width: '100%',
    height: '100%',
    backgroundColor: '#555',
  },
});


export default App;
