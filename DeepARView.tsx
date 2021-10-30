import React, { FC, useCallback, useImperativeHandle, useRef } from 'react';

import { findNodeHandle, requireNativeComponent, StyleProp, UIManager, ViewStyle } from 'react-native';

type DeepARProps = {
  onEventSent: (event: { nativeEvent: { type: string; value: string; value2: string } }) => void | Promise<void>;
  style?: StyleProp<ViewStyle>;
}

type DeepARMethods = 'flashInfo' | 'resume' | 'takeScreenshot'
  | 'startRecording' | 'stopRecording' | 'pause'
  | 'switchEffect' | 'switchCamera' | 'flashOn'
  | 'flashOff' | 'stop' | 'start';

const DeepARView = requireNativeComponent<DeepARProps>('DeepARView');

const DeepAR: FC<DeepARProps> = React.memo(React.forwardRef(({ onEventSent, style }, ref) => {
  const deepARRef = useRef(null);

  const callNativeMethod = useCallback((command: DeepARMethods, ...args: string[]) => {
    if (deepARRef.current) {
      UIManager.dispatchViewManagerCommand(
        findNodeHandle(deepARRef.current),
        command,
        args,
      );
    }
  }, []);

  useImperativeHandle(ref, () => ({
    callNativeMethod,
  }));

  return (
    <DeepARView
      ref={deepARRef}
      onEventSent={onEventSent}
      style={style}
    />
  );
}));

export default DeepAR;
