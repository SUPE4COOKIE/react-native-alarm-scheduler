module.exports = {
  dependency: {
    platforms: {
      android: {
        sourceDir: './android',
        packageImportPath: 'import com.rnalarmmodule.AlarmPackage;',
        packageInstance: 'new AlarmPackage()',
      },
      ios: {
        podspecPath: './react-native-alarmageddon.podspec',
      },
    },
  },
};