/*
 * @author tuituidan
 * @date 2023/11/24
 */
export default {
  name: "card-edit-index",
  components: {
    'category-select': () => import('@/components/category-select/index.vue'),
    'card-icon-select': () => import('@/components/card-icon-select/index.vue')
  },
  data() {
    return {
      uploadUrl: `${process.env.VUE_APP_BASE_API}/api/v1/upload/modules`,
      zipFileList: [],
      // 弹出层标题
      title: "",
      // 是否显示弹出层
      show: false,
      showFaviconLoading: false,
      isApply: false,
      // 菜单树选项
      categoryOptions: [],
      // 表单参数
      form: {
        type: 'default',
        category: '',
        title: '',
        content: '',
        privateContent: '',
        url: '',
        showQrcode: false,
        icon: null,
        zip: null,
      },
      types: [
        {
          id: 'default',
          name: '普通',
          disabled: false,
        },
        {
          id: 'zip',
          name: '静态网站',
          disabled: false,
        },
        {
          id: 'file',
          name: '文件',
          disabled: true,
        },
      ],
      // 表单校验
      rules: {
        category: [
          {required: true, message: "所属分类不能为空", trigger: "blur"}
        ],
        title: [
          {required: true, message: "标题不能为空", trigger: "blur"}
        ],
        content: [
          {required: true, message: "内容不能为空", trigger: "blur"}
        ],
        icon: [
          {required: true, message: "请添加一个图标", trigger: "change"},
        ],
        zip: [
          {required: true, message: '请上传网站zip文件'}
        ]
      }
    }
  },
  methods: {
    open(item = {}) {
      if (item.id) {
        this.title = '编辑卡片';
        this.form = {...item};
        if (item.zip) {
          this.zipFileList = [item.zip];
        }
      } else if(item.category) {
        this.form.type = 'default';
        this.form.category = item.category;
        this.title = '新增卡片';
      } else {
        this.form.type = 'default';
        this.title = '申请卡片';
        this.isApply = true;
      }
      this.show = true;
      this.$nextTick(() => {
        this.$refs.form.clearValidate()
        this.$refs.refCategory.init();
        this.$refs.refCardIcon.init(this.form.icon);
      })
    },
    zipFileRemove() {
      this.form.zip = null;
      this.zipFileList = [];
    },
    zipFileUploadSuccess(response, file) {
      this.form.zip = {
        name: file.name,
        path: response,
        isNew: true
      };
      this.zipFileList = [this.form.zip];
      this.$refs.form.validateField('zip');
    },
    /** 提交按钮 */
    submitForm: function () {
      this.$refs.form.validate(valid => {
        if (valid && this.form.icon) {
          const newZip = this.form.type === 'zip' && this.form.zip && this.form.zip.isNew;
          if (newZip) {
            this.$modal.loading('压缩包解压时间较长，请耐心等待...');
          }
          this.$http.save('/api/v1/card', {...this.form})
            .then(() => {
              this.$modal.msgSuccess('保存成功');
              this.show = false;
              this.$emit('refresh', this.form.category);
            })
            .finally(() => {
              if (newZip) {
                this.$modal.closeLoading();
              }
            })
        }
      });
    },
    // 取消按钮
    cancel() {
      this.show = false;
    },
    iconChange() {
      // 单个触发校验
      this.$refs.form.validateField('icon');
    },
    getFavicons() {
      if (!(this.form.url && this.form.url.startsWith('http'))) {
        return;
      }
      this.showFaviconLoading = true;
      this.$http.get('/api/v1/card/icon', {params: {url: this.form.url}})
        .then(res => {
          if (!(Array.isArray(res) && res.length > 0)) {
            return;
          }
          for (const url of res) {
            this.$refs.refCardIcon.uploadSuccess(url);
          }
        })
        .finally(() => {
          this.showFaviconLoading = false;
        });
    },
  },
}
